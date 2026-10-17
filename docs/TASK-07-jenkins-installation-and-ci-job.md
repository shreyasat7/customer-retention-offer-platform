# Task 7 — Jenkins Installation and Continuous Integration Job

**Controller:** Jenkins LTS **2.580.1** at <http://localhost:8080>
**Job:** `crop-ci-build` — freestyle Maven build, SCM-polling triggered
**Backlog item:** M11 · **Issue:** [#11](https://github.com/shreyasat7/customer-retention-offer-platform/issues/11)

---

## 1. Installation

```bash
brew install jenkins-lts          # Jenkins LTS 2.580.1
brew services start jenkins-lts   # launchd service, starts at login
```

| | |
|---|---|
| Listen address | `127.0.0.1:8080` — never exposed beyond the machine |
| `JENKINS_HOME` | `~/.jenkins` |
| Java | OpenJDK 21 (the controller's own runtime) |
| Service manager | launchd, via `brew services` |

```
$ curl -sI http://localhost:8080/login | grep X-Jenkins
X-Jenkins: 2.580.1
```

---

## 2. Configuration — declared, not clicked

The setup wizard was deliberately **skipped**, so the whole controller can be rebuilt from
an empty `JENKINS_HOME`. Two marker files turn the wizard off:

```bash
echo "2.580.1" > ~/.jenkins/jenkins.install.UpgradeWizard.state
echo "2.580.1" > ~/.jenkins/jenkins.install.InstallUtil.lastExecVersion
```

and three Groovy scripts in `~/.jenkins/init.groovy.d/` declare the rest. They are kept in
the repository under [`jenkins/`](../jenkins/):

| Script | Declares |
|---|---|
| `01-security.groovy` | Local user database, the `admin` account, *logged-in users get full control*, anonymous access denied |
| `02-tools.groovy` | `jdk17` → `/Library/Java/.../jdk-17.jdk/Contents/Home`, `maven3` → `/opt/homebrew/opt/maven/libexec` |
| `03-location.groovy` | The controller's own URL |

### Why `02-tools.groovy` matters

The host's default `mvn` runs on **JDK 27** while the project targets **17**. Pinning
`jdk17` as a Jenkins tool and selecting it on the job is what keeps the CI build on the
right runtime — project risk **R4**, closed here rather than left to the agent's `PATH`.

### Why `03-location.groovy` matters

Without a configured root URL the Jenkins CLI refuses to connect:

```
CLI handshake failed with status code 403
X-CLI-Error: Jenkins URL is not configured (set Jenkins URL in the configuration)
```

### Credentials

`admin` / `admin123` — a local demonstration controller bound to `127.0.0.1` with no real
data. The credential is *declared* in `01-security.groovy` so the controller is
reproducible, rather than being chosen interactively in a wizard.

---

## 3. Plugins

Installed through the CLI, not the wizard:

```bash
java -jar cli.jar -s http://localhost:8080/ -auth admin:admin123 install-plugin \
  git github workflow-aggregator pipeline-stage-view junit htmlpublisher \
  maven-plugin ws-cleanup timestamper build-timeout credentials-binding
java -jar cli.jar -s http://localhost:8080/ -auth admin:admin123 safe-restart
```

| Plugin | Version | Why |
|---|---|---|
| `git` | 5.11.0 | Clone and poll the GitHub repository |
| `github` | 1.47.0 | Link builds back to the repository |
| `maven-plugin` | 3.28 | The *Invoke top-level Maven targets* build step |
| `workflow-aggregator` | 608.v67378e9d3db1 | Declarative pipeline — used from Task 8 |
| `pipeline-stage-view` | 2.41 | The stage view — used from Task 8 |
| `junit` | 1442.vbba467df2767 | Publish surefire results |
| `htmlpublisher` | 429 | Publish test reports — used from Task 10 |
| `ws-cleanup` | 0.49 | Clean workspace between builds |
| `timestamper` | 1.30 | Timestamps in the console log |
| `build-timeout` | 1.41 | Abort a hung build |
| `credentials-binding` | 728.v902a273b8947 | Inject credentials — used from Task 12 |

**88 plugins installed in total** (11 requested plus their dependencies), all active.

---

## 4. The CI Job — `crop-ci-build`

Defined in [`jenkins/crop-ci-build.config.xml`](../jenkins/crop-ci-build.config.xml) and
created with one command, so it is reproducible rather than click-built:

```bash
java -jar cli.jar -s http://localhost:8080/ -auth admin:admin123 \
     create-job crop-ci-build < jenkins/crop-ci-build.config.xml
```

| Setting | Value |
|---|---|
| Type | Freestyle project |
| Source | `https://github.com/shreyasat7/customer-retention-offer-platform.git` |
| Branch | `*/develop` |
| JDK | `jdk17` |
| Build step | **Invoke top-level Maven targets** → `maven3`, goals `clean package` |
| Trigger | **Poll SCM** — `H/2 * * * *` (every two minutes) |
| Post-build | Archive `target/crop.war` with fingerprinting |
| Post-build | Publish JUnit results from `target/surefire-reports/*.xml` |
| Build wrapper | Timestamper |
| Retention | Last 25 builds, artefacts for the last 10 |

### Why polling rather than a webhook

A GitHub webhook cannot reach `127.0.0.1`, which is where this controller deliberately
lives (constraint C2 — everything runs on one laptop, nothing is exposed). Scheduled
polling achieves the same outcome — *a commit produces a build with no human action* —
and the task allows either. `H/2` spreads the poll across the two-minute window so several
jobs would not all hit GitHub at the same second.

---

## 5. Build Evidence

### Build #1 — triggered manually to verify the job

```
[INFO] Running com.crop.retention.service.OfferWorkflowTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.crop.retention.service.OfferTypeFormatTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- war:3.4.0:war (default-war) @ retention-platform ---
[INFO] Building war: /Users/shreyasathish/.jenkins/workspace/crop-ci-build/target/crop.war
[INFO] --- spring-boot:3.5.6:repackage (repackage) @ retention-platform ---
[INFO] BUILD SUCCESS
Archiving artifacts
Recording fingerprints
Recording test results
Finished: SUCCESS
```

### Build #2 — triggered by a commit, with no human action

A commit was pushed to `develop` at **00:53:39**. The SCM poll ran at **00:54:00**, found
the change, and started build #2:

```
Started on 18-Oct-2026, 12:54:00 am
Using strategy: Default
[poll] Last Built Revision: Revision ae08fc6e9143db5dc4d4fcf4e6e30ab503d49d08 (refs/remotes/origin/develop)
 > git ls-remote -h -- https://github.com/shreyasat7/customer-retention-offer-platform.git # timeout=10
Found 5 remote heads on https://github.com/shreyasat7/customer-retention-offer-platform.git
[poll] Latest remote head revision on refs/heads/develop is: 46ef3b030cdd7cab4cdd3b0f01ddcf7dd1a5abbb
Done. Took 0.62 sec
Changes found
```

**Elapsed from `git push` to a running build: 21 seconds.** This is success criterion
**SC6** — *commit → artefact with no human steps* — demonstrated.

### Archived artefact

| | |
|---|---|
| File | `crop.war` |
| Size | **60.69 MiB** |
| Where | *Last Successful Artifacts* on the job page, and on each build page |
| Fingerprinted | Yes — the same artefact can be traced into later builds |

### Published test results

21 tests, 0 failures, surfaced as the **Test Result Trend** graph on the job page and a
browsable report on each build.

---

## 6. Verification against the acceptance criteria

| Criterion (US-11) | Result |
|---|---|
| AC1 — job connected to the GitHub repository, triggered by SCM polling | ✅ `H/2 * * * *`, polling log shows *Changes found* |
| AC2 — runs `mvn clean package`, finishes SUCCESS | ✅ Builds #1 and #2 both SUCCESS |
| AC3 — WAR archived and downloadable from the build page | ✅ `crop.war`, 60.69 MiB, fingerprinted |
| AC4 — a push produces a build with no manual click | ✅ Build #2, 21 s after the push |

---

## 7. Rebuilding this controller from nothing

```bash
brew install jenkins-lts
mkdir -p ~/.jenkins/init.groovy.d
cp jenkins/0*.groovy ~/.jenkins/init.groovy.d/
echo "2.580.1" > ~/.jenkins/jenkins.install.UpgradeWizard.state
echo "2.580.1" > ~/.jenkins/jenkins.install.InstallUtil.lastExecVersion
brew services start jenkins-lts
# install plugins, then:
java -jar cli.jar -s http://localhost:8080/ -auth admin:admin123 \
     create-job crop-ci-build < jenkins/crop-ci-build.config.xml
```

---

## Deliverable checklist for Task 7

- [x] Jenkins installed and running — §1
- [x] Jenkins configured (security, tools, URL), reproducibly — §2
- [x] GitHub repository connected — §4
- [x] Maven build job created — §4
- [x] Triggered by scheduled polling — §4, §5
- [x] Successful build log — §5, `docs/logs/task-07-build-2-console.log`
- [x] Trigger evidence — §5, `task-07-scm-poll-log.png`
- [x] Archived artefact — §5, `task-07-build-artifact.png`
- [x] Evidence screenshots — `task-07-jenkins-dashboard.png`, `task-07-jenkins-job.png`, `task-07-build-console.png`, `task-07-scm-poll-log.png`, `task-07-build-artifact.png`, `task-07-test-results.png`
