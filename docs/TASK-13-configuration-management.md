# Task 13 — Configuration Management Script

**Tool:** Ansible core 2.21.5 · `community.docker` 5.4.0 · `community.general` 13.5.0
**Playbook:** [`ansible/site.yml`](../ansible/site.yml) · 3 roles · 29 tasks
**Backlog item:** M15 · **Issue:** [#15](https://github.com/shreyasat7/customer-retention-offer-platform/issues/15)

---

## 1. Configuration Specification

Everything a CROP application node requires. This is the contract the playbook enforces —
and it lives in [`ansible/group_vars/all.yml`](../ansible/group_vars/all.yml), so the
specification *is* the configuration rather than a document that drifts away from it.

### 1.1 Packages

| Package | Why | Managed by |
|---|---|---|
| `nginx` | Reverse proxy in front of the container | `community.general.homebrew` |
| `curl` | Health probing from the node itself | `community.general.homebrew` |
| Docker Engine | Container runtime | **Verified, not installed** — see §1.7 |

### 1.2 Users

| User | Where | Purpose |
|---|---|---|
| `crop` | **inside the container** | The application process; created by the `Dockerfile`, never root |
| The invoking user | on the node | Owns the platform directories and the Docker socket |

No node user is created. The application does not run on the host — it runs in a
container, where its user already exists. Creating a host user it would never use would be
configuration theatre.

### 1.3 Directories

| Path | Purpose |
|---|---|
| `~/crop-platform` | Platform root |
| `~/crop-platform/config` | Rendered configuration |
| `~/crop-platform/logs` | nginx access and error logs for the app |
| `~/crop-platform/releases` | Space for release artefacts |

### 1.4 Files

| File | Rendered from | Contents |
|---|---|---|
| `~/crop-platform/config/crop.env` | inline `copy` | Image, container name, ports, volume, health URL |
| `~/crop-platform/config/node-spec.md` | `node-spec.md.j2` | This specification, rendered **for the node itself** |
| `/opt/homebrew/etc/nginx/servers/crop.conf` | `crop.conf.j2` | The reverse-proxy server block |

### 1.5 Ports

| Port | Purpose | Checked how |
|---|---|---|
| 8090 | Application container (published) | `lsof` inventory, reported per port |
| 8081 | nginx reverse proxy | same |
| 5001 | Docker registry | `uri` probe against `/v2/` — the play fails early if unreachable |
| 8099 | Where Homebrew nginx's stock default server is moved to | `replace` in `nginx.conf` |

> Homebrew's stock `nginx.conf` listens on **8080**, which Jenkins already holds. The
> playbook moves it to 8099 rather than leaving nginx unable to start.

### 1.6 Services

| Service | Desired state | Managed how |
|---|---|---|
| Docker Engine | reachable | `docker info`, `changed_when: false` — a check must never report a change |
| `crop-app` container | started, healthy | `community.docker.docker_container` |
| `crop-data` volume | present | `community.docker.docker_volume` |
| nginx | started | `brew services`, with `changed_when` on the actual outcome |

### 1.7 Deliberately *not* managed

| Not managed | Why |
|---|---|
| **Installing Docker Engine** | Docker Desktop is a GUI application on macOS. The playbook *verifies* the daemon and fails fast with a clear message, rather than pretending it can install it |
| **Contents of the data volume** | Created if missing, never otherwise touched. That is what makes the Task 14 rollback non-destructive |
| **Jenkins and Tomcat** | They are the delivery toolchain, not part of an application node |

---

## 2. Inventory

```ini
[crop_app]
crop-node-01 ansible_connection=local ansible_host=localhost

[crop_app:vars]
node_role=application
node_environment=demo
ansible_python_interpreter={{ ansible_playbook_python }}
```

| Line | Why |
|---|---|
| `ansible_connection=local` | Constraint C2 — one laptop. **No task in any role assumes it is local**; swapping in a real host means changing these two lines and nothing else |
| `node_role`, `node_environment` | Host facts the roles report and template, so the same playbook can describe `demo` and `prod` nodes |
| `ansible_python_interpreter` | Auto-discovery picked Homebrew's `python3.14`, which has no Docker SDK, and every `community.docker` task failed. `ansible_playbook_python` is the interpreter Ansible itself runs on — the one where the SDK is installed |

---

## 3. Playbook Structure

```
ansible/
├── ansible.cfg              inventory, roles path, no privilege escalation
├── inventory.ini            the target node
├── group_vars/all.yml       THE configuration specification (§1)
├── site.yml                 provision + deploy
├── rollback.yml             roll back to a previously published image tag
├── teardown.yml             return the node to a clean state
└── roles/
    ├── prerequisites/       packages, runtime, registry, dirs, files, volume, ports
    ├── app_container/       pull image, run container, health check
    └── nginx_proxy/         reverse proxy on :8081
```

### Why three roles rather than one long playbook

Each role owns one concern and can be run alone with `--tags`:

```bash
ansible-playbook site.yml --tags prereq    # just the node prerequisites
ansible-playbook site.yml --tags deploy    # just the container
ansible-playbook site.yml --tags proxy     # just nginx
```

### `prerequisites`

Packages → verify the container runtime → probe the registry → directories → files →
data volume → port inventory. The registry probe comes **before** anything is created, so
an unreachable registry fails the run in seconds instead of halfway through a deploy.

### `app_container`

```yaml
- name: Run the application container
  community.docker.docker_container:
    name: "{{ crop_container_name }}"
    docker_host: "{{ crop_docker_host }}"
    image: "{{ crop_image }}"
    state: started
    recreate: false          # recreate only when declared state differs from reality
    published_ports: ["{{ crop_container_port }}:{{ crop_internal_port }}"]
    volumes: ["{{ crop_data_volume }}:/app/data"]
```

`recreate: false` is what makes a second run report `changed=0`: the module compares the
declared container against the running one and acts only on a genuine difference.

The role then waits for `/actuator/health` to report `UP` **and** for Docker's own health
state to reach `healthy` — two independent confirmations.

### `nginx_proxy`

Renders the server block, **validates the configuration with `nginx -t` before touching
the service**, ensures nginx is running, then proves the proxy actually serves the
application before declaring success.

---

## 4. Two Problems Found and Fixed While Writing This

| Symptom | Cause | Fix |
|---|---|---|
| `Failed to import the required Python library (requests)` | Ansible auto-discovered Homebrew's `python3.14`; the Docker SDK is installed in Ansible's own interpreter | `ansible_python_interpreter={{ ansible_playbook_python }}` in the inventory |
| `Error while fetching server API version: FileNotFoundError` | Docker Desktop's socket is at `~/.docker/run/docker.sock`, not `/var/run/docker.sock` where the SDK looks | `crop_docker_host` declared in `group_vars` and passed to every `community.docker` task |

Both are recorded because both are the sort of environment-specific trap that costs an
afternoon the second time if it is not written down.

---

## 5. First Execution

The node was first returned to a clean state with `teardown.yml`:

```
$ ansible-playbook teardown.yml -e confirm=yes -e wipe_data=true
"container       : removed"
"proxy config    : removed"
"nginx           : stopped"
"directory tree  : removed"
"data volume     : removed"
crop-node-01 : ok=7  changed=5  unreachable=0  failed=0
```

verified clean:

```
$ docker ps -a --filter name=crop-app        # (nothing)
$ docker volume ls --filter name=crop-data   # (nothing)
$ ls -d ~/crop-platform                      # No such file or directory
$ ls /opt/homebrew/etc/nginx/servers/crop.conf  # No such file or directory
```

Then the playbook was run against that clean node:

```
$ ansible-playbook site.yml

TASK [prerequisites : Report the node this play is configuring]
    "Configuring crop-node-01 (Darwin MacOSX, role=application, environment=demo)"
TASK [prerequisites : Ensure required Homebrew packages are installed]    ok
TASK [prerequisites : Verify the container runtime is reachable]          ok
    "Docker Engine 29.8.2 is available"
TASK [prerequisites : Fail early if the image registry is not reachable]  ok
TASK [prerequisites : Ensure the platform directory tree exists]          changed (4 items)
TASK [prerequisites : Write the node environment file]                    changed
TASK [prerequisites : Record the configuration specification on the node] changed
TASK [prerequisites : Ensure the application data volume exists]          changed
TASK [prerequisites : Report port allocation]                             ok
TASK [app_container : Pull the application image from the registry]       changed
TASK [app_container : Run the application container]                      changed
TASK [app_container : Wait for the application to report healthy]         ok
    "HEALTH UP — db=UP, disk=UP"
TASK [nginx_proxy : Move the stock nginx default server off the Jenkins port] changed
TASK [nginx_proxy : Install the CROP reverse-proxy server block]          changed
TASK [nginx_proxy : Validate the nginx configuration]                     ok
TASK [nginx_proxy : Ensure nginx is running]                              changed
TASK [nginx_proxy : Wait for the proxy to serve the application]          ok
    "Reverse proxy healthy at http://localhost:8081 -> container :8090"

TASK [Summarise the provisioned node]
    "node        : crop-node-01 (demo)"
    "image       : localhost:5001/crop:latest"
    "container   : crop-app on :8090"
    "direct URL  : http://localhost:8090"
    "proxied URL : http://localhost:8081"
    "config      : /Users/shreyasathish/crop-platform/config/crop.env"

PLAY RECAP
crop-node-01 : ok=29  changed=8  unreachable=0  failed=0  skipped=0
```

Full log: `docs/logs/task-13-first-execution.log`.

---

## 6. Verification against the acceptance criteria

| Criterion (US-15) | Result |
|---|---|
| AC1 — specification of packages, users, directories, files, ports, services | ✅ §1, and rendered onto the node as `node-spec.md` |
| AC2 — Ansible inventory and YAML playbook provision the node | ✅ §2, §3, §5 |
| AC3 — a second run reports `changed=0` | ⏭ Task 14 |
| AC4 — health check after provisioning | ✅ §3, and Task 14 |
| AC5 — rollback demonstrated | ⏭ Task 14 |

---

## Deliverable checklist for Task 13

- [x] Server prerequisites identified — packages, users, folders, files, ports, services — §1
- [x] Configuration specification — §1, encoded in `group_vars/all.yml`, rendered onto the node
- [x] Ansible inventory — §2
- [x] YAML playbook with roles — §3
- [x] First execution log — §5, `docs/logs/task-13-first-execution.log`
- [x] Evidence screenshot — `task-13-first-execution.png`
