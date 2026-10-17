# Jenkins configuration

Everything needed to rebuild this controller from an empty `JENKINS_HOME`.

## Controller

| | |
|---|---|
| Version | Jenkins LTS 2.580.1 |
| Installed by | `brew install jenkins-lts` |
| Runs as | `brew services start jenkins-lts` (launchd, starts at login) |
| URL | <http://localhost:8080> — bound to `127.0.0.1`, never exposed |
| `JENKINS_HOME` | `~/.jenkins` |
| Credentials | `admin` / `admin123` — local demonstration instance, no real data |

## Configuration as code

The setup wizard is deliberately skipped. Everything is declared in
`~/.jenkins/init.groovy.d/`, which this directory mirrors:

| Script | What it declares |
|---|---|
| `01-security.groovy` | Local user database, the `admin` account, "logged-in users get full control", anonymous denied |
| `02-tools.groovy` | `jdk17` → the JDK 17 home, `maven3` → the Homebrew Maven home |
| `03-location.groovy` | The controller's own URL (without it the Jenkins CLI refuses to connect) |

Pinning the JDK in `02-tools.groovy` is what keeps the build off the host's default
JDK 27 while the project targets 17 (project risk R4).

## Plugins

Installed through the CLI rather than the wizard:

```bash
java -jar cli.jar -s http://localhost:8080/ -auth admin:admin123 install-plugin \
  git github workflow-aggregator pipeline-stage-view junit htmlpublisher \
  maven-plugin ws-cleanup timestamper build-timeout credentials-binding
```

## Jobs

| Job | Definition | Purpose |
|---|---|---|
| `crop-ci-build` | `crop-ci-build.config.xml` | Task 7 — freestyle Maven CI build, SCM polling, archived artefact |
| `crop-pipeline` | created from the repository `Jenkinsfile` | Task 8 onward — the declarative pipeline |

Recreate a job with:

```bash
java -jar cli.jar -s http://localhost:8080/ -auth admin:admin123 \
     create-job crop-ci-build < jenkins/crop-ci-build.config.xml
```
