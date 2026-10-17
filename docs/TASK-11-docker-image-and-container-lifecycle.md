# Task 11 — Docker Image and Container Lifecycle

**Image:** `crop:1.0.0` / `crop:latest` · 569 MB · `linux/arm64` · 11 layers
**Container:** `crop-app` on **http://localhost:8090** · healthy
**Backlog item:** M14 · **Issue:** [#14](https://github.com/shreyasat7/customer-retention-offer-platform/issues/14)

---

## 1. The Dockerfile

[`Dockerfile`](../Dockerfile) — runtime-only, by deliberate choice.

### Why not build the application inside the image

A multi-stage image that runs Maven would produce a **different binary** from the one the
Selenium gate just tested. The pipeline builds the WAR once, tests *that* WAR, and ships
*that* WAR into the image — so the artefact that passed the gate is byte-for-byte the
artefact that runs. Rebuilding inside the image would mean testing one binary and
shipping another.

### Decisions, and the reason for each

| Instruction | Why |
|---|---|
| `FROM eclipse-temurin:17-jre` | Matches the project's JDK 17 target. **Not** `:17-jre-alpine` — Temurin publishes no `linux/arm64` Alpine variant, and this host is Apple silicon (the first build failed with `no match for platform in manifest`) |
| `LABEL org.opencontainers.image.*` | Standard OCI metadata, so the image is self-describing in a registry |
| `apt-get install curl` + `rm -rf /var/lib/apt/lists/*` | `curl` is required by `HEALTHCHECK`; the list cache is removed in the same layer so it is not baked into the image |
| `JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"` | The JVM sizes itself from the *container's* limit, not the host's; serial GC is right for a small single-CPU container |
| `groupadd`/`useradd` + `USER crop` | A container that does not need root should not have it |
| `COPY --chown=crop:crop` | Ownership is set during the copy rather than in an extra `chown` layer |
| `VOLUME ["/app/data"]` | The H2 database outlives the container. **This is what makes the Task 14 rollback non-destructive** |
| `HEALTHCHECK` | Docker itself reports whether the *application* is up, so `docker ps` shows `(healthy)` rather than merely `Up` |
| `ENTRYPOINT ["/bin/sh","-c","exec java $JAVA_OPTS …"]` | JSON form with an explicit shell, because `$JAVA_OPTS` must be word-split at runtime. `exec` replaces the shell so the JVM is PID 1 and receives `SIGTERM` on `docker stop` |
| `.dockerignore` excludes everything but `target/crop.war` | Keeps the build context at ~61 MB and stops unrelated source edits from busting the cache |

### Linting

The Dockerfile was checked with **hadolint**:

```
$ hadolint Dockerfile
Dockerfile:44 DL3066 info: Non-numeric user-id may not be resolvable by host system
```

Two earlier findings were fixed rather than ignored — `DL3025` (shell-form `ENTRYPOINT`)
by switching to JSON form with an explicit shell, and `DL3009` by clearing the apt lists
in the same layer. `DL3025` on the `HEALTHCHECK` carries an explicit
`# hadolint ignore` with the reason: the probe pipes `curl` into `grep`, which JSON form
cannot express. `DL3066` is informational — a named user reads better than a bare uid.

---

## 2. Build and Tag

```bash
docker build -t crop:1.0.0 -t crop:latest .
```

```
$ docker images crop
REPOSITORY   TAG       IMAGE ID       CREATED          SIZE
crop         1.0.0     32efcc873ab2   21 seconds ago   569MB
crop         latest    32efcc873ab2   21 seconds ago   569MB
```

Both tags point at the **same image ID** — `latest` is a moving pointer at the newest
build, `1.0.0` is the immutable one you can roll back to.

```
$ docker image inspect crop:1.0.0 --format '{{.Os}}/{{.Architecture}} layers={{len .RootFS.Layers}} user={{.Config.User}} exposed={{...}}'
linux/arm64  layers=11  user=crop  exposed=8080/tcp
```

```
$ docker history crop:1.0.0
0B      ENTRYPOINT ["/bin/sh" "-c" "exec java $JAVA_…
0B      HEALTHCHECK {Test:[CMD-SHELL curl -fsS "http…
0B      EXPOSE [8080/tcp]
0B      USER crop
0B      VOLUME [/app/data]
12.3kB  RUN /bin/sh -c mkdir -p /app/data && chown -…
```

---

## 3. Complete Container Lifecycle

### 3.1 Run, with port mapping and a named volume

```bash
docker run -d --name crop-app \
    -p 8090:8080 \
    -v crop-data:/app/data \
    --restart unless-stopped \
    crop:1.0.0
```

```
$ docker ps --filter name=crop-app
NAMES      IMAGE        STATUS                    PORTS
crop-app   crop:1.0.0   Up 50 seconds (healthy)   0.0.0.0:8090->8080/tcp, [::]:8090->8080/tcp
```

| Flag | Effect |
|---|---|
| `-d` | Detached — the container keeps running after the shell exits |
| `-p 8090:8080` | Host **8090** → container **8080**. The app always listens on 8080 *inside*; only the host side moves |
| `-v crop-data:/app/data` | Named volume for the H2 database |
| `--restart unless-stopped` | Comes back after a Docker or machine restart, unless deliberately stopped |

### 3.2 Logs

```
$ docker logs crop-app --tail 6
01:34:01.769 INFO  o.s.b.w.e.tomcat.TomcatWebServer : Tomcat started on port 8080 (http) with context path '/'
01:34:01.776 INFO  c.crop.retention.RetentionApplication : Started RetentionApplication in 2.742 seconds
01:34:01.896 INFO  com.crop.retention.config.DataSeeder : Seeded 24 demonstration offers
```

### 3.3 Inspect

```
$ docker inspect crop-app --format 'health={{.State.Health.Status}} restarts={{.RestartCount}} pid={{.State.Pid}}'
health=healthy restarts=0 pid=883

$ docker inspect crop-app --format '{{range .Mounts}}{{.Type}} {{.Name}} -> {{.Destination}}{{end}}'
volume crop-data -> /app/data

$ docker exec crop-app ls -l /app/data
-rw-r--r-- 1 crop crop 32768 Oct 18 01:34 retention.mv.db
```

Note the file is owned by `crop`, not `root` — the unprivileged user works end to end.

### 3.4 Resource usage

```
$ docker stats crop-app --no-stream
NAME       CPU %   MEM USAGE / LIMIT     MEM %   NET I/O
crop-app   3.03%   412.4MiB / 7.748GiB   5.20%   1.78kB / 1.17kB
```

### 3.5 Stop

```
$ docker stop crop-app
crop-app

$ docker ps -a --filter name=crop-app
NAMES      STATUS
crop-app   Exited (143) Less than a second ago

$ curl --max-time 4 http://localhost:8090/actuator/health
app while stopped: 000
```

Exit code **143** = `128 + 15 (SIGTERM)` — the JVM received the signal and shut down
cleanly, which is the payoff of `exec` in the entrypoint.

### 3.6 Start — and the volume proves itself

```
$ docker start crop-app
crop-app
NAMES      STATUS
crop-app   Up 45 seconds (healthy)

$ docker logs crop-app | grep Seeded
01:34:01.896 INFO  DataSeeder : Seeded 24 demonstration offers              <- first run
01:35:06.628 INFO  DataSeeder : Database already contains 24 offers - skipping seed
```

**The seeder did not run again.** The data survived in the volume.

### 3.7 Restart

```
$ docker restart crop-app
crop-app
NAMES      STATUS
crop-app   Up 45 seconds (healthy)
```

### 3.8 Remove — and recreate against the same data

```
$ docker rm -f crop-app
crop-app

$ docker ps -a --filter name=crop-app
NAMES     STATUS
# container gone

$ docker volume ls --filter name=crop-data
DRIVER    VOLUME NAME
local     crop-data
```

The volume **survives the container**. Recreating it:

```
$ docker run -d --name crop-app -p 8090:8080 -v crop-data:/app/data --restart unless-stopped crop:1.0.0
NAMES      IMAGE        STATUS                    PORTS
crop-app   crop:1.0.0   Up 45 seconds (healthy)   0.0.0.0:8090->8080/tcp
01:36:37.057 INFO  DataSeeder : Database already contains 24 offers - skipping seed
```

A brand-new container, the same data. **This is the mechanism the Task 12 automatic
redeploy and the Task 14 rollback both depend on** — the container is disposable, the
data is not.

### 3.9 Lifecycle summary

| Command | State before | State after |
|---|---|---|
| `docker build` | — | image `crop:1.0.0`, `crop:latest` |
| `docker run -d` | image only | `Up (healthy)`, port 8090 published |
| `docker logs` | running | unchanged — reads stdout |
| `docker inspect` / `stats` | running | unchanged — reads metadata |
| `docker stop` | `Up` | `Exited (143)`, port released |
| `docker start` | `Exited` | `Up (healthy)`, **data intact** |
| `docker restart` | `Up` | `Up (healthy)` |
| `docker rm -f` | `Up` | container gone, **volume kept** |
| `docker run` again | volume only | `Up (healthy)`, **same data** |

Full transcript: `docs/logs/task-11-container-lifecycle.log`.

---

## 4. Running Container Evidence

| | |
|---|---|
| Application | <http://localhost:8090> — the full UI, served from the container |
| Health | <http://localhost:8090/actuator/health> → `{"status":"UP"}` |
| Docker status | `Up (healthy)` — Docker's own `HEALTHCHECK`, not just "the process exists" |

Screenshot: `docs/screenshots/task-11-containerised-app.png`.

---

## 5. A Docker Interface to Demonstrate

Three views are available live:

| Interface | Where | Shows |
|---|---|---|
| **Dozzle** | <http://localhost:8092> | Web UI over the Docker socket: every container, live status, CPU and memory, and streaming logs. No account needed |
| **Docker Desktop** | the macOS app | Images, containers, volumes and logs in Docker's own GUI |
| **CLI** | terminal | `docker ps`, `logs`, `inspect`, `stats` — §3 |

Dozzle runs as a container itself:

```bash
docker run -d --name dozzle -p 8092:8080 \
    -v /var/run/docker.sock:/var/run/docker.sock:ro \
    --restart unless-stopped amir20/dozzle:v8.14.4
```

The socket is mounted **read-only**: the dashboard can observe the daemon but not command
it. Screenshot: `docs/screenshots/task-11-dozzle-interface.png`.

---

## 6. Port Allocation

| Port | Container |
|---|---|
| 8090 | `crop-app` — the application |
| 8092 | `dozzle` — the Docker web interface |
| 5001 / 5002 | the local registry and its UI (Task 12) |

---

## 7. Verification against the acceptance criteria

| Criterion (US-14) | Result |
|---|---|
| AC1 — a Dockerfile builds the image; the full lifecycle is documented | ✅ §1, §3 |
| AC2 — image tagged with a version and `latest` | ✅ `crop:1.0.0` + `crop:latest` (pipeline tagging in Task 12) |
| AC3 — pushed to a registry, visible in a catalog | ⏭ Task 12 |
| AC4 — a fresh container replaces the previous one after tests pass | ⏭ Task 12 |

---

## Deliverable checklist for Task 11

- [x] Dockerfile — §1, linted with hadolint
- [x] Image built and tagged — §2
- [x] Image details — §2 (id, size, arch, layers, user, history)
- [x] Container run with port mapping — §3.1
- [x] Logs inspected — §3.2
- [x] Container stopped, restarted, removed — §3.5–§3.8
- [x] Complete lifecycle documented — §3.9, `docs/logs/task-11-container-lifecycle.log`
- [x] Running container evidence — §4
- [x] Evidence screenshots — `task-11-image-details.png`, `task-11-container-lifecycle.png`, `task-11-containerised-app.png`, `task-11-dozzle-interface.png`
