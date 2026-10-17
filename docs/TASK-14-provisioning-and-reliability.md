# Task 14 — Automated Provisioning and Reliability Validation

**Provisioned node:** `crop-node-01` → container on **:8090**, nginx proxy on **:8081**
**Idempotency:** second run `changed=0` · **Rollback:** `crop:13` → `crop:11` in **11 s**
**Backlog item:** M15 · **Issue:** [#15](https://github.com/shreyasat7/customer-retention-offer-platform/issues/15)

---

## 1. Provisioning a Clean Environment

### 1.1 Making it clean

[`ansible/teardown.yml`](../ansible/teardown.yml) returns the node to nothing. It refuses
to run without `-e confirm=yes`, and it keeps the data volume unless `wipe_data=true` is
passed as well — **removing a database should never be something a playbook does by
accident.**

```
$ ansible-playbook teardown.yml -e confirm=yes -e wipe_data=true
"container       : removed"
"proxy config    : removed"
"nginx           : stopped"
"directory tree  : removed"
"data volume     : removed"
crop-node-01 : ok=7  changed=5  failed=0
```

Verified clean — container, volume, directories and proxy config all absent.

### 1.2 Provisioning from nothing

```
$ ansible-playbook site.yml
crop-node-01 : ok=29  changed=8  unreachable=0  failed=0  skipped=0
```

Eight changes, each one a thing that did not exist: four directories, two configuration
files, the data volume, the image pull, the container, the nginx server block and the
nginx service.

| Result | Value |
|---|---|
| Container | `crop-app`, `localhost:5001/crop:latest`, **healthy** |
| Direct URL | <http://localhost:8090> |
| Proxied URL | <http://localhost:8081> |
| Data | 24 offers, seeded into the fresh volume on first start |

Log: `docs/logs/task-13-first-execution.log`.

---

## 2. Idempotency

The same playbook, run again immediately, against the node it just built:

```
$ ansible-playbook site.yml

PLAY RECAP
crop-node-01 : ok=28  changed=0  unreachable=0  failed=0  skipped=0
```

**`changed=0`** — success criterion **SC9**. Log: `docs/logs/task-14-idempotency.log`.

### It did not start that way — three tasks reported `changed` forever

| Task | Why it always changed | Fix |
|---|---|---|
| `Record the configuration specification on the node` | The template embedded `{{ ansible_date_time.iso8601 }}`. A render timestamp differs on every run, so the file content differed, so the task changed | Replaced with `{{ ansible_managed }}`, which is static |
| `Install the CROP reverse-proxy server block` | Same render timestamp in `crop.conf.j2` | Same fix |
| `Reload nginx` (handler) | `changed_when: true` — the handler *declared* a change rather than reporting one | `changed_when: "'RELOADED' in nginx_reload.stdout"` |

> **The lesson this records:** a timestamp inside a managed file destroys the idempotency
> signal. The task reports `changed` on every run, so a real drift is indistinguishable
> from the clock ticking. Both templates now carry a comment saying exactly that, because
> adding a "rendered at" line is a natural thing to want to do.

### How idempotency is achieved

| Technique | Where |
|---|---|
| Declarative modules — `file`, `copy`, `template`, `docker_container`, `docker_volume` | Throughout; they compare declared state to actual state |
| `recreate: false` on `docker_container` | The container is replaced only when the declared spec genuinely differs |
| `changed_when: false` on every read | `docker info`, `docker inspect`, `nginx -t`, `lsof`, all `uri` probes — a check must never report a change |
| `changed_when` on the real outcome | The nginx start task reports changed only when it actually started the service |
| No timestamps in managed files | §2 above |

---

## 3. Health Check

Three independent confirmations, not one:

```
$ curl -s http://localhost:8090/actuator/health      # the container directly
{"status": "UP", "db": "UP", "disk": "UP"}

$ curl -s http://localhost:8081/actuator/health      # through the nginx reverse proxy
{"status": "UP", "db": "UP", "disk": "UP"}

$ curl -o /dev/null -w "%{http_code} (%{time_total}s)" http://localhost:8081/login
200 (0.149537s)

$ docker inspect crop-app --format '{{.State.Health.Status}} {{.Config.Image}}'
healthy  localhost:5001/crop:latest
```

| Probe | Proves |
|---|---|
| Container directly | The application is up |
| Through nginx | The proxy is correctly wired, not just running |
| A real page, not only the health endpoint | The servlet, templates and static assets all work |
| Docker's own `HEALTHCHECK` | The runtime agrees, independently of anything Ansible did |

The playbook itself gates on the first and the last: the `uri` task retries for up to
120 s, and the Docker health state is polled until it reaches `healthy`. A node that does
not come up **fails the play** rather than being reported as provisioned.

Log: `docs/logs/task-14-health-check.log`.

---

## 4. Rollback and Recovery

### 4.1 Setting up a meaningful rollback

Builds #10 and #11 shared a content digest — the only change between them was excluded by
`.dockerignore`, so the images were byte-identical (Task 12 §5). Rolling back between
identical images would prove nothing.

So a **real, visible change** was released first: the application version was bumped to
**1.1.0** and surfaced in the sidebar. The pipeline built and deployed `crop:13` from it.

| | Before rollback |
|---|---|
| Image | `localhost:5001/crop:13` |
| Sidebar | `OFFER PLATFORM · V1.1.0` |
| Offers | 24 |

### 4.2 The rollback

[`ansible/rollback.yml`](../ansible/rollback.yml) takes the tag to return to, and refuses
to act on a tag that was never published:

```yaml
- name: Fail if the requested tag was never published
  ansible.builtin.assert:
    that:
      - rollback_to in (registry_tags.json.tags | default([]))
    fail_msg: >-
      Tag '{{ rollback_to }}' is not in the registry.
      Published tags: {{ registry_tags.json.tags | default([]) | join(', ') }}
```

```
$ ansible-playbook rollback.yml -e rollback_to=11

TASK [Require a target tag]                       "Rolling back to tag 11"
TASK [Confirm the target tag exists in the registry]   ok
TASK [Fail if the requested tag was never published]   "Tag 11 is present in the registry"
TASK [app_container : Pull the application image]      changed
TASK [app_container : Run the application container]   changed
TASK [app_container : Wait for the application to report healthy]
    "HEALTH UP — db=UP, disk=UP"
TASK [Report the rollback]
    "rolled back from : localhost:5001/crop:13"
    "rolled back to   : localhost:5001/crop:11"
    "health           : UP"
    "data volume      : crop-data (untouched)"

PLAY RECAP
crop-node-01 : ok=13  changed=1  unreachable=0  failed=0
```

**Wall-clock time: 11 seconds** — success criterion **SC10** is `< 2 minutes`.

### 4.3 What actually changed, and what did not

```
$ docker inspect crop-app --format 'image={{.Config.Image}} health={{.State.Health.Status}}'
image=localhost:5001/crop:11  health=healthy

$ docker logs crop-app | grep -i seed
DataSeeder : Database already contains 24 offers - skipping seed
```

| | Before | After rollback |
|---|---|---|
| Image | `crop:13` | **`crop:11`** |
| Sidebar version | `· V1.1.0` | **absent** (the 1.0.0 UI) |
| Total offers | 24 | **24** |
| Awaiting approval | 4 | **4** |
| Acceptance rate | 77.8% | **77.8%** |
| Revenue retained | ₹35,108 | **₹35,108** |
| Data volume | `crop-data` | **`crop-data`, untouched** |

Screenshots `task-14-before-rollback-v110.png` and `task-14-after-rollback-v100.png` are
the same dashboard before and after: **the code reverted, every number stayed.**

The seeder log line is the proof that matters — it found an existing database rather than
creating one, so the rollback replaced the running code without touching a single record.

### 4.4 Recovery is symmetric

```
$ ansible-playbook rollback.yml -e rollback_to=13
crop-node-01 : ok=13  changed=1  failed=0

$ docker inspect crop-app --format 'image={{.Config.Image}} health={{.State.Health.Status}}'
image=localhost:5001/crop:13 health=healthy
```

The same playbook moves in either direction, because "roll back" and "roll forward" are
the same operation: *deploy a named, published image tag and verify it is healthy.*

Log: `docs/logs/task-14-rollback.log`.

---

## 5. Why this is safe

| Property | Mechanism |
|---|---|
| Rollback cannot lose data | The data volume is never removed by `site.yml` or `rollback.yml`. Only `teardown.yml` can, and only with an explicit `wipe_data=true` |
| Rollback cannot deploy a phantom version | The tag is checked against the registry's own tag list before anything is touched |
| A failed rollback is visible | The play fails if the application or Docker does not report healthy; it does not report success on a dead container |
| Any release is reachable | Every pipeline run publishes an immutable numbered tag, so any previous build is a rollback target |
| Ordinary runs are safe to repeat | `changed=0` means re-running the playbook on a healthy node does nothing at all |

---

## 6. Success criteria closed

| ID | Criterion | Target | Result |
|---|---|---|---|
| SC9 | Re-running the playbook changes nothing | `changed=0` | ✅ §2 |
| SC10 | Rollback to the previous image | `< 2 min` | ✅ **11 s** |

---

## 7. Verification against the acceptance criteria

| Criterion (US-15) | Result |
|---|---|
| AC1 — configuration specification | ✅ Task 13 §1 |
| AC2 — inventory and playbook provision a clean environment and deploy the container | ✅ §1 |
| AC3 — second run reports `changed=0` | ✅ §2 |
| AC4 — health check confirms the application responds | ✅ §3 |
| AC5 — rollback demonstrated and the application recovers | ✅ §4 |

---

## Deliverable checklist for Task 14

- [x] Clean target environment provisioned with Ansible — §1
- [x] Application container deployed by the playbook — §1
- [x] Idempotency demonstrated (`changed=0`), with the three non-idempotent tasks found and fixed — §2
- [x] Health check performed, from three independent angles — §3
- [x] Rollback to the previous stable release — §4
- [x] Recovery demonstrated — §4.4
- [x] Evidence screenshots — `task-13-first-execution.png`, `task-14-idempotency.png`, `task-14-health-check.png`, `task-14-rollback.png`, `task-14-before-rollback-v110.png`, `task-14-after-rollback-v100.png`
