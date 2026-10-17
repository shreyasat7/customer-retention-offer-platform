# Customer Retention Offer Platform (CROP)

A role-based web application for raising, approving and tracking the retention offers a
contact-centre team uses to save at-risk customers — delivered end to end by an automated
DevOps pipeline.

> DevOps mini-project · 14 tasks · Git → Jenkins → Maven → Selenium → Docker → Ansible

[![build](https://img.shields.io/badge/build-maven-blue)](pom.xml)
[![java](https://img.shields.io/badge/java-17%20LTS-orange)](pom.xml)
[![spring boot](https://img.shields.io/badge/spring%20boot-3.5.6-green)](pom.xml)
[![packaging](https://img.shields.io/badge/packaging-war-lightgrey)](pom.xml)

---

## Why this exists

Retention offers are usually created in spreadsheets, approved verbally and tracked
nowhere. Discount margin leaks, approved offers are never actually sent, and nobody can
prove which offers worked. CROP makes the approval step part of the workflow and keeps a
single searchable record of every offer, with a dashboard over the whole pipeline.

## What it does

| Capability | Detail |
|---|---|
| **Create** | Raise an offer against a customer, segment, churn-risk score and monthly value |
| **View** | Paginated list and a detail page carrying the full audit trail |
| **Update** | Edit an offer while it is still a draft, or after it has been rejected |
| **Search** | Free text across case reference, name and email, combined with status / segment / risk filters |
| **Role-based workflow** | `DRAFT → PENDING_APPROVAL → APPROVED → SENT → ACCEPTED \| DECLINED`, plus `REJECTED` and revision — each transition restricted by role |
| **Dashboard** | Acceptance rate, revenue retained, status funnel, risk breakdown, approval queue |

### Roles

| Role | Can do |
|---|---|
| `ROLE_AGENT` | Create, edit drafts, submit for approval, send approved offers, record the outcome |
| `ROLE_MANAGER` | Approve or reject with a decision comment |
| `ROLE_ADMIN` | Everything above |

---

## Quick start

```bash
git clone https://github.com/shreyasat7/customer-retention-offer-platform.git
cd customer-retention-offer-platform

. scripts/env.sh          # pins JAVA_HOME to JDK 17 and exports the port map
mvn clean package         # -> target/crop.war
./scripts/run-local.sh    # http://localhost:8070
```

Or run the built artefact exactly the way the container does:

```bash
APP_PORT=8070 java -jar target/crop.war
```

### Demo accounts

| Username | Password | Role |
|---|---|---|
| `agent` | `agent123` | Retention Agent |
| `manager` | `manager123` | Retention Manager |
| `admin` | `admin123` | Administrator |

> This is a local demonstration instance holding synthetic data only. The accounts are
> deliberately well known and the instance is never exposed beyond localhost.

---

## Technology

| Layer | Choice |
|---|---|
| Runtime | Java 17 (LTS) |
| Framework | Spring Boot 3.5.6 — Web, Security, Data JPA, Validation, Actuator |
| View | Thymeleaf + a hand-written CSS design system (no CDN assets) |
| Database | H2, file mode (`./data/retention.mv.db`) |
| Build | Maven 3.10, WAR packaging, artefact `crop.war` |
| CI/CD | Jenkins LTS, declarative `Jenkinsfile` |
| Testing | JUnit 5 (unit) + Selenium WebDriver with headless Chrome (UI) |
| Containers | Docker, local `registry:2` with a web interface |
| Config management | Ansible |
| Servers | Apache Tomcat 11 (WAR) and Nginx (reverse proxy) |

**The WAR is both executable and deployable** — `java -jar crop.war` for the container,
`webapps/crop.war` for Tomcat. One artefact, two deployment targets.

---

## Port map

| Port | Service |
|---|---|
| 8070 | Application — local development |
| 8095 | Application instance the Selenium suite runs against |
| 8080 | Jenkins |
| 9090 | Apache Tomcat (WAR deployment target) |
| 8090 | Application Docker container |
| 8081 | Nginx reverse proxy |
| 5001 | Local Docker registry |
| 5002 | Docker registry web interface |
| 8092 | Dozzle — Docker web interface |

---

## Repository layout

```
.
├── pom.xml                   Maven build — WAR packaging, finalName=crop
├── Jenkinsfile               declarative CI/CD pipeline
├── Dockerfile                container image for the application
├── .mvn/maven.config         -B --no-transfer-progress
├── ansible/                  inventory, playbook and roles for provisioning
├── scripts/
│   ├── env.sh                pins JAVA_HOME to JDK 17, exports the port map
│   ├── run-local.sh          run the app locally
│   └── evidence/             screenshot helpers used to produce docs/screenshots
├── src/main/java/com/crop/retention/
│   ├── config/               security configuration and data seeding
│   ├── model/                RetentionOffer + Segment / OfferType / OfferStatus
│   ├── repository/           Spring Data JPA repository with the search query
│   ├── service/              OfferService and OfferWorkflow — business rules live here
│   └── web/                  Spring MVC controllers
├── src/main/resources/
│   ├── templates/            Thymeleaf views
│   └── static/css/app.css    the complete design system
├── src/test/java/            unit tests and Selenium integration tests
└── docs/                     one document and screenshot set per project task
```

---

## Branching policy

| Branch | Purpose | How code gets in |
|---|---|---|
| `main` | Release-ready baseline only | Merged from `develop`, tagged on merge (`v1.0.0`) |
| `develop` | Integration branch for completed stories | Pull request from `feature/*` only |
| `feature/<short-name>` | Exactly one user story | Branched from `develop`, merged back by PR |
| `bugfix/<issue-number>-<short-name>` | A defect fix | Same rules as `feature/*` |

**Rules**

1. Never commit directly to `main` or `develop`.
2. One branch per backlog item; keep it short-lived.
3. Branch names are lower-case kebab-case: `feature/create-view-offer`.
4. Every branch is merged through a pull request that carries at least one review comment.
5. Commit messages follow `type(scope): subject` — see [CONTRIBUTING.md](CONTRIBUTING.md).
6. `main` is tagged on every release with a semantic version.

---

## Documentation

Each project task has its own document and screenshot evidence under [`docs/`](docs/).

| Task | Document |
|---|---|
| 1 | [Problem definition and scope](docs/TASK-01-problem-definition-and-scope.md) |
| 2 | [Agile planning and DevOps workflow](docs/TASK-02-agile-planning-and-devops-workflow.md) |
| 3 | [Requirements, architecture and technology setup](docs/TASK-03-requirements-architecture-and-setup.md) |
| 4 | [Git and GitHub repository initialization](docs/TASK-04-git-and-github-initialization.md) |
| 5 | [Feature development with branching](docs/TASK-05-feature-development-with-branching.md) |
| 6 | [MVP completion and Git collaboration](docs/TASK-06-mvp-completion-and-git-collaboration.md) |
| 7 | [Jenkins installation and CI job](docs/TASK-07-jenkins-installation-and-ci-job.md) |
| 8 | [Pipeline as code and server deployment](docs/TASK-08-pipeline-as-code-and-deployment.md) |
| 9 | [Selenium test design and local execution](docs/TASK-09-selenium-test-design.md) |
| 10 | [Continuous testing in Jenkins](docs/TASK-10-continuous-testing-in-jenkins.md) |
| 11 | [Docker image and container lifecycle](docs/TASK-11-docker-image-and-container-lifecycle.md) |
| 12 | [Jenkins–Docker continuous deployment](docs/TASK-12-jenkins-docker-continuous-deployment.md) |
| 13 | [Configuration management script](docs/TASK-13-configuration-management.md) |
| 14 | [Automated provisioning and reliability validation](docs/TASK-14-provisioning-and-reliability.md) |

---

## Health check

```bash
curl -s http://localhost:8070/actuator/health
# {"status":"UP","components":{"db":{"status":"UP", ...
```

`/actuator/health` is deliberately unauthenticated — it is the probe used by the Jenkins
deploy stage and by the Ansible health check.
