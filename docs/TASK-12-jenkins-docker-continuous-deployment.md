# Task 12 — Jenkins–Docker Continuous Deployment

**Registry:** `localhost:5001` (`registry:2`) · **Web interface:** <http://localhost:5002>
**Versioned image:** `localhost:5001/crop:<BUILD_NUMBER>` + `:latest`
**Deployed container:** `crop-app` on <http://localhost:8090>
**Backlog item:** M14 · **Issue:** [#14](https://github.com/shreyasat7/customer-retention-offer-platform/issues/14)

---

## 1. The Registry

A local `registry:2` rather than Docker Hub — no account, no credentials in Jenkins, works
offline, and still a *real* registry speaking the real v2 API (constraint C2).

```bash
docker network create crop-net

docker run -d --name registry --network crop-net -p 5001:5000 \
    -e REGISTRY_STORAGE_DELETE_ENABLED=true \
    -v registry-data:/var/lib/registry \
    --restart unless-stopped registry:2

docker run -d --name registry-ui --network crop-net -p 5002:80 \
    -e SINGLE_REGISTRY=true \
    -e REGISTRY_TITLE="CROP Image Registry" \
    -e NGINX_PROXY_PASS_URL=http://registry:5000 \
    -e DELETE_IMAGES=true -e SHOW_CONTENT_DIGEST=true -e SHOW_CATALOG_NB_TAGS=true \
    --restart unless-stopped joxit/docker-registry-ui:2.5.7
```

| Decision | Reason |
|---|---|
| Both containers on `crop-net`, UI proxies via `NGINX_PROXY_PASS_URL` | The browser only ever talks to the UI, so there is no CORS configuration to get wrong |
| `-v registry-data:/var/lib/registry` | Pushed images survive a registry restart |
| `REGISTRY_STORAGE_DELETE_ENABLED=true` | Old tags can be reclaimed |
| Port **5001**, not 5000 | macOS AirPlay Receiver already holds 5000 |
| No TLS | Docker treats `localhost` registries as insecure-allowed, so no daemon configuration is needed |

### Catalog API

```
$ curl -s http://localhost:5001/v2/_catalog
{"repositories":["crop"]}

$ curl -s http://localhost:5001/v2/crop/tags/list
{"name":"crop","tags":["latest","10","11"]}
```

The web interface at <http://localhost:5002> lists the repository, every tag, its content
digest, architecture and size — screenshots `task-12-registry-ui.png` and
`task-12-registry-tags.png`.

---

## 2. Pipeline Extension

Four stages were added **after** the Tomcat health check, all behind the Selenium gate —
so an image is never built from a revision that failed its tests.

| Stage | What it does |
|---|---|
| **Build Docker image** | `docker build -t <registry>/crop:${BUILD_NUMBER} -t <registry>/crop:latest .` then inspects the result |
| **Publish to registry** | Pushes both tags, then prints the catalog and the tag list into the build log |
| **Deploy container** | `docker pull` the published tag, remove the old container, `docker run` a fresh one |
| **Container health check** | Polls `/actuator/health` for up to 120 s; prints `docker logs` and fails the build if it never reports `UP` |

### Versioning

```groovy
IMAGE_TAG    = "${registry}/${name}:${env.BUILD_NUMBER}"
IMAGE_LATEST = "${registry}/${name}:latest"
```

The **Jenkins build number is the version**: monotonic, unique, and traceable straight
back to the run that produced it. `:latest` is a moving pointer for convenience; the
numbered tag is the one you roll back to (Task 14).

### Pull before deploy

```groovy
docker pull "${IMAGE_TAG}"
docker rm -f "${CONTAINER_NAME}" || true
docker run -d --name "${CONTAINER_NAME}" -p "${CONTAINER_PORT}:8080" \
    -v crop-data:/app/data --restart unless-stopped \
    --label "crop.build=${BUILD_NUMBER}" --label "crop.commit=${GIT_SHA}" \
    "${IMAGE_TAG}"
```

Three deliberate choices:

1. **Pull rather than reuse the local build.** The deploy exercises the artefact that was
   actually *published*, not a look-alike sitting in the local image cache. If the push
   were broken, this stage would catch it.
2. **The named volume is never removed.** The container is disposable; the data is not.
3. **`crop.build` and `crop.commit` labels.** A running container can be traced back to
   its build and its commit without guesswork — used as evidence in §4.

### Making the Docker CLI reachable

```groovy
PATH = "/usr/local/bin:/opt/homebrew/bin:${env.PATH}"
```

launchd starts Jenkins with a minimal `PATH` that does not include `/usr/local/bin`, where
Docker Desktop's CLI symlink lives. Without this the stage fails with `docker: command not
found`.

---

## 3. Parameters

| Parameter | Default | Purpose |
|---|---|---|
| `DOCKER_REGISTRY` | `localhost:5001` | Publish somewhere else without editing the pipeline |
| `IMAGE_NAME` | `crop` | Repository name inside the registry |
| `CONTAINER_NAME` | `crop-app` | Lets a second instance run side by side |
| `CONTAINER_PORT` | `8090` | Host port the container publishes |
| `SKIP_DOCKER` | `false` | Build and test only, without touching containers |

---

## 4. End-to-End Evidence: commit → container

A commit was pushed to `develop` at **01:43:21**. **Nothing else was done by hand.**

```
build #11 auto-triggered after 60s        <- SCM poll, not a button
build #11 -> SUCCESS
```

### What the pipeline did

```
built sha256:1127480adb9c…  linux/arm64  568558803 bytes

+ curl -s http://localhost:5001/v2/_catalog
{"repositories":["crop"]}
+ curl -s http://localhost:5001/v2/crop/tags/list
{"name":"crop","tags":["latest","10","11"]}

Replacing crop-app with localhost:5001/crop:11
+ docker rm -f crop-app
+ docker run -d --name crop-app -p 8090:8080 -v crop-data:/app/data \
    --restart unless-stopped --label crop.build=11 --label crop.commit=5bfff0b \
    localhost:5001/crop:11

NAMES      IMAGE                    STATUS                                   PORTS
crop-app   localhost:5001/crop:11   Up Less than a second (health: starting) 0.0.0.0:8090->8080/tcp

Container healthy after 6s
{"status":"UP","components":{"db":{"status":"UP", …

SUCCESS — 9a119be deployed to http://localhost:9090/crop
         container crop-app running localhost:5001/crop:11 on http://localhost:8090
```

### Proving the running container is the commit

```
$ docker inspect crop-app --format 'build={{index .Config.Labels "crop.build"}} commit={{index .Config.Labels "crop.commit"}}'
build=11 commit=5bfff0b

$ git rev-parse --short HEAD
5bfff0b
```

**The container's commit label equals the repository HEAD.** That is the whole task in two
commands.

---

## 5. An Honest Observation About the Digests

The registry interface shows tags `10`, `11` and `latest` sharing one content digest:

```
sha256:e9714b33c076e1f105687235873b61287a9defffa8b2de681b11a2edcafea356
```

That is **correct**, not a bug. The only change between builds #10 and #11 was
`README.md`, which `.dockerignore` excludes from the build context. The image layers were
therefore byte-identical, and the registry's content-addressable storage stored them once.

Two useful consequences:

- A documentation-only commit produces a new *tag* without consuming new storage.
- It is a reminder that **the tag is the deployment identity, the digest is the content
  identity**, and they are not the same thing.

For Task 14 the rollback is demonstrated against images whose content genuinely differs,
so that reverting is visible in the running application rather than only in a tag name.

---

## 6. Full Pipeline

```
Checkout → Build → Unit tests → Package
        → Selenium regression gate  ◀── a failure here stops everything below
        → Deploy to Tomcat → Health check
        → Build Docker image → Publish to registry
        → Deploy container → Container health check
```

Eleven stages. A single `git push` traverses all of them. Total run time ≈ 1 min 30 s,
inside success criterion **SC7** (< 6 min).

---

## 7. Verification against the acceptance criteria

| Criterion (US-14) | Result |
|---|---|
| AC1 — Dockerfile builds the image; lifecycle documented | ✅ Task 11 |
| AC2 — image tagged with the build number **and** `latest` | ✅ `crop:11` + `crop:latest` |
| AC3 — pushed to a registry and visible in its catalog | ✅ §1, §4, registry UI |
| AC4 — after tests pass, a fresh container replaces the previous one | ✅ §4, build #11 |

Success criterion **SC6** (commit → artefact with no human steps) now extends all the way
to a running container.

---

## Deliverable checklist for Task 12

- [x] Versioned Docker image built by the pipeline — §2
- [x] Published to a registry — §1, §4
- [x] Registry evidence — catalog API and web interface — §1
- [x] Fresh container deployed automatically after successful tests — §2, §4
- [x] End-to-end commit-to-container pipeline — §4, §6
- [x] Evidence screenshots — `task-12-registry-ui.png`, `task-12-registry-tags.png`, `task-12-pipeline-full.png`, `task-12-commit-to-container.png`
