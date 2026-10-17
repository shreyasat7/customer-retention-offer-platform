# Task 8 — Pipeline as Code and Server Deployment

**Pipeline definition:** [`Jenkinsfile`](../Jenkinsfile) in the repository root
**Jenkins job:** `crop-pipeline` — [`jenkins/crop-pipeline.config.xml`](../jenkins/crop-pipeline.config.xml)
**Deployment target:** Apache Tomcat 11.0.27 on port **9090**
**Deployed URL:** <http://localhost:9090/crop>
**Backlog item:** M12 · **Issue:** [#12](https://github.com/shreyasat7/customer-retention-offer-platform/issues/12)

---

## 1. Why pipeline as code

The Task 7 job was click-configured through Jenkins. That works, but the delivery process
then lives only inside `JENKINS_HOME` — it is not reviewed, not versioned, and not visible
in a pull request.

Moving the stages into a `Jenkinsfile` means **a change to how the software is delivered is
reviewed exactly like a change to the software**. The two commits in this task are proof:
`feat(ci): add the declarative delivery pipeline` and the follow-up fix both went through
the repository, not through a web form.

---

## 2. The Pipeline

```
Checkout → Build → Unit tests → Package → Deploy to Tomcat → Health check
```

| Stage | What it does |
|---|---|
| **Checkout** | `checkout scm`, then records the short SHA, branch and commit subject into the build description so the Jenkins build list reads like a git log |
| **Build** | `mvn -B clean compile` — fails fast on a compile error before tests are attempted |
| **Unit tests** | `mvn -B test`; results published through `junit` in a `post { always }` block so they appear even when the stage fails |
| **Package** | `mvn -B package -DskipTests` (tests already ran), then archives `target/crop.war` with fingerprinting |
| **Deploy to Tomcat** | Removes the previous exploded directory *and* WAR, then copies the new artefact into `webapps/` |
| **Health check** | Polls `/actuator/health` for up to 120 s; fails the build and prints `catalina.out` if the application never reports `UP` |

### Toolchain pinning

```groovy
tools {
    jdk   'jdk17'
    maven 'maven3'
}
```

The host's default `mvn` runs on JDK 27 while the project targets 17. Pinning both in the
`tools` block closes project risk **R4** inside the pipeline rather than relying on the
agent's `PATH`.

---

## 3. Parameterisation

The task asks for **at least one** environment setting to be parameterised. Four are:

```groovy
parameters {
    string(name: 'TOMCAT_HOME', defaultValue: '/opt/homebrew/opt/tomcat/libexec',
           description: 'CATALINA_HOME of the Tomcat the WAR is deployed into')
    string(name: 'DEPLOY_PORT', defaultValue: '9090',
           description: 'HTTP port the deployment target listens on')
    string(name: 'APP_CONTEXT', defaultValue: 'crop',
           description: 'Context path the application is deployed under')
    booleanParam(name: 'SKIP_DEPLOY', defaultValue: false,
           description: 'Build and test only — do not touch the server')
}
```

| Parameter | Lets you |
|---|---|
| `TOMCAT_HOME` | Point the same pipeline at a different Tomcat installation |
| `DEPLOY_PORT` | Deploy to a second instance on another port |
| `APP_CONTEXT` | Deploy the same build side by side, e.g. `/crop` and `/crop-uat` |
| `SKIP_DEPLOY` | Validate a branch without touching the running server |

These are visible and editable in Jenkins under **Build with Parameters**
(`docs/screenshots/task-08-pipeline-parameters.png`).

### Resolving parameters safely

```groovy
environment {
    // Parameters only exist from the second build onward — the first run of a
    // parameterised job is what *defines* them.
    TOMCAT_HOME = "${params.TOMCAT_HOME ?: '/opt/homebrew/opt/tomcat/libexec'}"
    DEPLOY_PORT = "${params.DEPLOY_PORT ?: '9090'}"
    APP_CONTEXT = "${params.APP_CONTEXT ?: 'crop'}"
}
```

This was not written defensively from the start — see §5.

---

## 4. Tomcat Configuration

| Setting | Value | Why |
|---|---|---|
| HTTP connector | `8080` → **`9090`** | 8080 belongs to Jenkins |
| Shutdown port | `8005` → **`9005`** | Avoid a second conflict |
| `JRE_HOME` | JDK 17 | The host default is 27 |
| `CROP_DATA_DIR` | `~/crop-data/tomcat` | The H2 file database gets an absolute home instead of depending on whatever directory Tomcat started in |

`bin/setenv.sh`:

```bash
export JRE_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export JAVA_HOME="$JRE_HOME"
export CATALINA_OPTS="$CATALINA_OPTS -Xms256m -Xmx768m"
export CROP_DATA_DIR="$HOME/crop-data/tomcat"
```

The WAR is dropped into `webapps/crop.war` and Tomcat auto-deploys it — the deployment is
visible as a file on disk, which makes it easy to demonstrate.

---

## 5. Pipeline Run Evidence

### Build #1 — FAILURE, and why that is useful

```
+ set -eu
script.sh.copy: line 3: TOMCAT_HOME: unbound variable
Stage "Health check" skipped due to earlier failure(s)
FAILURE — d1196a3 was not deployed
```

**Cause.** A parameterised Jenkins job has no parameters on its first build — *that build
is what defines them*. So `params.TOMCAT_HOME` was null, nothing was exported to the
shell, and `set -eu` correctly refused to continue with an unbound variable.

**Fix.** `fix(ci): resolve pipeline parameters with defaults in the environment block` —
each parameter is resolved in the `environment` block with the same default, so the first
build and any rebuild from an older revision both work.

The failure is worth keeping in the history: it shows `set -eu` doing its job, and it
shows the deploy stage **stopping** rather than deploying something half-configured.

### Build #2 — SUCCESS

```
+ echo 'Deploying to /opt/homebrew/opt/tomcat/libexec/webapps/crop.war'
+ rm -rf /opt/homebrew/opt/tomcat/libexec/webapps/crop
+ rm -f  /opt/homebrew/opt/tomcat/libexec/webapps/crop.war
+ cp target/crop.war /opt/homebrew/opt/tomcat/libexec/webapps/crop.war
-rw-r--r--  1 shreyasathish  admin  61M Oct 18 00:59 .../webapps/crop.war

+ echo 'Waiting for http://localhost:9090/crop/actuator/health'
+ status=404      # Tomcat is still expanding the WAR
+ status=404
+ status=200
Healthy after 9s
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"H2", …

Build #2 finished with status SUCCESS
SUCCESS — 3fba0f3 deployed to http://localhost:9090/crop
```

### Stage view

| Stage | #1 | #2 |
|---|---|---|
| Declarative: Checkout SCM | 6 s ✅ | 1 s ✅ |
| Declarative: Tool Install | 222 ms ✅ | 234 ms ✅ |
| Checkout | 3 s ✅ | 3 s ✅ |
| Build | 2 s ✅ | 2 s ✅ |
| Unit tests | 3 s ✅ | 3 s ✅ |
| Package | 3 s ✅ | 3 s ✅ |
| Deploy to Tomcat | 795 ms ❌ **failed** | 747 ms ✅ |
| Health check | 95 ms ❌ **failed** | 7 s ✅ |

**Full run time ~24 s** — comfortably inside success criterion SC7 (< 6 min).

The two red cells next to the green row are the clearest possible illustration of the
point: when a stage fails, nothing downstream runs.

---

## 6. Deployed Application

| | |
|---|---|
| URL | <http://localhost:9090/crop> |
| Health | <http://localhost:9090/crop/actuator/health> → `{"status":"UP"}` |
| Server | Apache Tomcat 11.0.27 |
| Artefact | `webapps/crop.war`, 61 MB |
| Database | `~/crop-data/tomcat/retention.mv.db` — the Tomcat instance seeds its own copy |

The deployed instance is a genuinely separate deployment from the development one on
`:8070`: it has its own data directory, its own seeded database and its own lifecycle.
Screenshot: `docs/screenshots/task-08-deployed-tomcat.png`.

---

## 7. Verification against the acceptance criteria

| Criterion (US-12) | Result |
|---|---|
| AC1 — declarative `Jenkinsfile` with Checkout, Build, Test, Package, Deploy | ✅ Six stages |
| AC2 — at least one environment setting parameterised | ✅ Four parameters |
| AC3 — pipeline deploys the WAR to Tomcat; the URL responds 200 | ✅ Health check gates the build on it |
| AC4 — stage view shows every stage green | ✅ Build #2 |

---

## Deliverable checklist for Task 8

- [x] `Jenkinsfile` with checkout, build, package and deploy stages — §2
- [x] At least one environment setting parameterised — §3 (four)
- [x] Successful pipeline run — §5, `docs/logs/task-08-pipeline-2-console.log`
- [x] Deployed application URL — <http://localhost:9090/crop> — §6
- [x] Configuration evidence — §4 (Tomcat ports, `setenv.sh`), §3 (parameters)
- [x] Evidence screenshots — `task-08-pipeline-stage-view.png`, `task-08-pipeline-console.png`, `task-08-pipeline-parameters.png`, `task-08-deployed-tomcat.png`
