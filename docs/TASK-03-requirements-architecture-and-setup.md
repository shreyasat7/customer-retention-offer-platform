# Task 3 — Requirements, Architecture and Technology Setup

**Project:** Automated Customer Retention Offer Platform (CROP)
**Deliverables:** SRS summary · use-case diagram · architecture diagram · data model and API list · working local setup

---

## 1. SRS Summary

### 1.1 Purpose
Specify the minimum software needed to let a retention team raise, approve, send and
measure customer retention offers through an enforced workflow, and to deliver that
software through an automated DevOps pipeline.

### 1.2 Scope
CROP is a single server-rendered web application with one aggregate (`RetentionOffer`),
three roles and seven workflow states. It covers **create, view, update and search**
records, a **role-based status workflow** and a **summary dashboard** — nothing more
(the frozen 15-item scope from Task 1).

### 1.3 Product perspective
A standalone internal tool. It consumes a churn-risk score produced elsewhere (entered
with the offer) and produces KPI figures consumed by Finance. No live integrations.

### 1.4 User classes
| Class | Role token | Rights |
|---|---|---|
| Retention Agent | `ROLE_AGENT` | Create, edit drafts, submit, send, record outcome, search, view |
| Retention Manager | `ROLE_MANAGER` | Approve, reject with comment, search, view, dashboard |
| Administrator | `ROLE_ADMIN` | All of the above |

### 1.5 Functional requirements

| ID | Requirement | Priority | MVP item |
|---|---|---|---|
| FR-01 | The system shall authenticate users with a username and password and establish a role-bearing session. | Must | M1 |
| FR-02 | The system shall deny access to every application URL for unauthenticated requests, redirecting to the login page. | Must | M1 |
| FR-03 | An Agent shall be able to create a retention offer capturing customer, segment, churn risk, monthly value, offer type, offer value and notes. | Must | M2 |
| FR-04 | The system shall assign each new offer a unique case reference of the form `CRO-####` and set its status to `DRAFT`. | Must | M2 |
| FR-05 | The system shall reject invalid input (blank name, malformed email, risk outside 0–100, non-positive amounts) with field-level messages and persist nothing. | Must | M2 |
| FR-06 | The system shall list offers with pagination of 10 records per page. | Must | M3 |
| FR-07 | The system shall display a single offer with all commercial fields and its complete audit trail. | Must | M4 |
| FR-08 | An Agent shall be able to edit an offer while it is in `DRAFT` or `REJECTED`, and the system shall refuse edits in any other state. | Must | M5 |
| FR-09 | The system shall search offers by case reference, customer name or customer email, case-insensitively and on partial matches. | Must | M6 |
| FR-10 | The system shall filter offers by status, segment and minimum churn risk, combinable with the text search. | Must | M6 |
| FR-11 | The system shall permit only the transitions defined in §4.3 and shall refuse all others. | Must | M7 |
| FR-12 | The system shall restrict `approve`/`reject` to Managers and `submit`/`send`/`outcome` to Agents. | Must | M7 |
| FR-13 | The system shall require a non-empty comment when an offer is rejected. | Must | M8 |
| FR-14 | The system shall stamp the acting username and timestamp onto every workflow transition. | Must | M7, M8 |
| FR-15 | The system shall present a dashboard with total offers, offers awaiting approval, acceptance rate and monthly revenue retained. | Must | M9 |
| FR-16 | The dashboard shall show the count of offers in each of the seven states and a churn-risk band breakdown. | Must | M9 |
| FR-17 | The dashboard shall list the oldest offers still awaiting approval. | Should | M9 |
| FR-18 | The system shall expose an unauthenticated health endpoint for deployment probes. | Must | M12, M15 |

### 1.6 Non-functional requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Performance | Any page shall render in under 2 s on the demo machine with 100 seeded records. |
| NFR-02 | Security | Passwords shall be stored BCrypt-hashed; all state-changing requests shall carry a CSRF token. |
| NFR-03 | Security | Authorisation shall be enforced server-side; hiding a button is never the only control. |
| NFR-04 | Auditability | Created-by, submitted-by, decided-by and sent-by, each with a timestamp, shall be immutable once written. |
| NFR-05 | Portability | The same artefact shall run as an executable WAR and deploy to an external Tomcat. |
| NFR-06 | Portability | The UI shall use no external fonts, scripts or CDN assets so it renders identically offline and inside a container. |
| NFR-07 | Maintainability | Business rules shall live in a service layer, not in controllers or templates. |
| NFR-08 | Testability | Every workflow rule shall be reachable by a unit test without a browser. |
| NFR-09 | Operability | The application shall start in under 10 s and expose `/actuator/health` returning `UP`. |
| NFR-10 | Usability | The UI shall be legible at 1280 px and usable down to 900 px. |

### 1.7 Assumptions and dependencies
- JDK 17, Maven, Docker, Jenkins, Ansible and Google Chrome are available on the host.
- The churn-risk score is produced upstream; CROP stores and displays it.
- Only one node is provisioned; no clustering or shared session store is required.

---

## 2. Use-Case Model

### 2.1 Actors
| Actor | Type | Description |
|---|---|---|
| Retention Agent | Primary, human | Raises and progresses offers |
| Retention Manager | Primary, human | Approves or rejects offers |
| Administrator | Primary, human | Superset of both, operates the platform |
| Jenkins Pipeline | Secondary, system | Polls `/actuator/health` after deployment |
| Ansible Controller | Secondary, system | Provisions the node and health-checks the container |

### 2.2 Use-case diagram

```
                    CUSTOMER RETENTION OFFER PLATFORM
    ┌──────────────────────────────────────────────────────────────────┐
    │                                                                  │
    │      ( UC1  Sign in / sign out )                                 │
    │                                                                  │
    │      ( UC2  Create retention offer )                             │
    │                                                                  │
    │      ( UC3  View offer list )                                    │
    │                 ⋮ «include»                                      │
    │      ( UC4  Search and filter offers )                           │
    │                                                                  │
    │      ( UC5  View offer detail and audit trail )                  │
    │                                                                  │
    │      ( UC6  Edit draft offer )                                   │
    │                                                                  │
    │      ( UC7  Submit offer for approval )                          │
    │                                                                  │
    │      ( UC8  Approve offer )  ⋯«extend»⋯▶ ( UC8a Add comment )    │
    │                                                                  │
    │      ( UC9  Reject offer )   ⋯«include»⋯▶( UC8a Add comment )    │
    │                                                                  │
    │      ( UC10 Revise rejected offer )                              │
    │                                                                  │
    │      ( UC11 Send approved offer to customer )                    │
    │                                                                  │
    │      ( UC12 Record customer outcome )                            │
    │                                                                  │
    │      ( UC13 View summary dashboard )                             │
    │                                                                  │
    │      ( UC14 Probe application health )                           │
    │                                                                  │
    └──────────────────────────────────────────────────────────────────┘
         ▲                    ▲                  ▲              ▲
         │                    │                  │              │
   ┌─────┴──────┐      ┌──────┴──────┐    ┌──────┴─────┐  ┌─────┴──────┐
   │ Retention  │      │  Retention  │    │   Admin    │  │  Jenkins / │
   │   Agent    │      │   Manager   │    │            │  │  Ansible   │
   └────────────┘      └─────────────┘    └────────────┘  └────────────┘
    UC1-UC7,            UC1, UC3-UC5,      all UC1-UC13       UC14
    UC10-UC13           UC8, UC9, UC13
```

### 2.3 Use-case specification — UC7 Submit offer for approval (representative)

| Field | Value |
|---|---|
| **Actor** | Retention Agent |
| **Pre-condition** | Agent is signed in; offer exists in status `DRAFT` |
| **Trigger** | Agent clicks **Submit for approval** on the offer detail page |
| **Main flow** | 1. System verifies the caller holds `ROLE_AGENT`. 2. System verifies the current status is `DRAFT`. 3. System sets status to `PENDING_APPROVAL`. 4. System stamps `submittedBy` and `submittedAt`. 5. System persists and redirects to the detail page with a confirmation. |
| **Alternate A1** | Status is not `DRAFT` → system refuses with "Cannot submit an offer in status X" and leaves the record unchanged. |
| **Alternate A2** | Caller is a Manager → HTTP 403; no change. |
| **Post-condition** | Offer appears in the Manager's Approvals queue and the sidebar badge increments. |

---

## 3. Architecture

### 3.1 Style
A classic **layered monolith**, server-rendered. Chosen deliberately over microservices:
constraint C1 (one developer, two weeks) and C7 (demonstrable in a 20-minute viva).
A single artefact also makes the Docker and Tomcat deployment stories simple and
directly comparable.

### 3.2 Application layers

```
 ┌──────────────────────────────────────────────────────────────────────┐
 │ PRESENTATION   Thymeleaf templates + app.css (no JS framework, no CDN)│
 │                layout.html · login · offers/list · form · detail      │
 │                dashboard                                              │
 ├──────────────────────────────────────────────────────────────────────┤
 │ WEB            Spring MVC @Controller                                 │
 │                OfferController · DashboardController · LoginController│
 │                GlobalModelAdvice · GlobalExceptionHandler             │
 ├──────────────────────────────────────────────────────────────────────┤
 │ SECURITY       Spring Security filter chain                           │
 │                form login · BCrypt · ROLE_AGENT/MANAGER/ADMIN · CSRF  │
 ├──────────────────────────────────────────────────────────────────────┤
 │ SERVICE        OfferService (use cases) · OfferWorkflow (state rules) │
 │                — the only place a status transition may be decided    │
 ├──────────────────────────────────────────────────────────────────────┤
 │ PERSISTENCE    Spring Data JPA · RetentionOfferRepository             │
 ├──────────────────────────────────────────────────────────────────────┤
 │ DATABASE       H2 file database (./data/retention.mv.db)              │
 └──────────────────────────────────────────────────────────────────────┘
```

**Rule:** a controller never changes a status directly; it calls `OfferWorkflow`.
That single choke point is what makes success criterion SC1 ("zero offers sent without
approval") testable without a browser.

### 3.3 Deployment architecture

```
  Developer                GitHub                   Jenkins :8080
  ─────────                ──────                   ─────────────
  git push ──────────────▶ develop ──SCM poll────▶  ┌──────────────────┐
                                                    │ Checkout         │
                                                    │ Build (mvn)      │
                                                    │ Unit test        │
                                                    │ Package crop.war │
                                                    │ Selenium GATE ───┼──fail──▶ STOP
                                                    │ Deploy           │
                                                    │ Docker build+push│
                                                    └────────┬─────────┘
                                   ┌─────────────────────────┼──────────────────┐
                                   ▼                         ▼                  ▼
                        Tomcat 11 :9090            Docker registry :5001   Container :8090
                        webapps/crop.war           crop:<build>, :latest   java -jar crop.war
                                                   UI at :5002                   │
                                                                                 ▼
                                                                      Nginx reverse proxy :8081
                                                                                 │
                                                                      provisioned by Ansible
                                                                      health-checked, rollback-able
```

### 3.4 Technology selection and rationale

| Concern | Choice | Why this one |
|---|---|---|
| Language / runtime | **Java 17 (LTS)** | Matches the Maven/Tomcat/Jenkins toolchain the module prescribes; LTS avoids surprises. The host's default `mvn` runs on JDK 27, so `JAVA_HOME` is pinned to 17 in `scripts/env.sh` and in the `Jenkinsfile` (risk R4). |
| Framework | **Spring Boot 3.5.6** | Gives web, security, persistence, validation and a health endpoint in one dependency set, so the DevOps work is the focus rather than plumbing. |
| UI | **Thymeleaf + hand-written CSS** | Server-rendered HTML is far more stable for Selenium than a SPA, and avoids a second (npm) build chain. No CDN assets, satisfying NFR-06. |
| **Build tool** | **Maven 3.10** | Prescribed option; `mvn clean package` is a single, scriptable command for Jenkins, and the Failsafe/Surefire split cleanly separates unit tests from Selenium ITs. |
| Packaging | **WAR (`crop.war`)** | One artefact serves both deployment targets — executable for Docker (`java -jar`), deployable for Tomcat. Demonstrates the Tomcat requirement without a second build. |
| Database | **H2, file mode** | Constraint C4 (no DB licence). File mode (not in-memory) means data survives a container restart, which the Task 14 rollback demo depends on. |
| **Deployment target** | **Apache Tomcat 11** (`:9090`) | Prescribed option; the WAR drop into `webapps/` is visible and easy to demonstrate. |
| Reverse proxy | **Nginx** (`:8081`) | Fronts the container in the Ansible-provisioned environment, giving a realistic production shape. |
| CI/CD | **Jenkins LTS** (`:8080`) | Prescribed; declarative `Jenkinsfile` keeps the pipeline versioned with the code. |
| UI testing | **Selenium WebDriver + headless Chrome** | Prescribed; headless mode lets the same suite run locally and in Jenkins. |
| Containers | **Docker + `registry:2`** (`:5001`, UI `:5002`) | A local registry needs no cloud account and still demonstrates a real push/pull and a browsable catalog. |
| Config management | **Ansible** | Agentless, YAML, runs against `localhost` as the target node — no VM required (constraint C2). |

### 3.5 Local port map

| Port | Service |
|---|---|
| 8070 | Application, local development (`mvn spring-boot:run`) |
| 8095 | Application instance the Selenium suite runs against in the pipeline |
| 8080 | Jenkins |
| 9090 | Apache Tomcat (WAR deployment target) |
| 8090 | Application Docker container |
| 8081 | Nginx reverse proxy in front of the container |
| 5001 | Local Docker registry |
| 5002 | Docker registry web interface |

---

## 4. Data Model

### 4.1 Entity–relationship

The MVP has a single aggregate. Segment, offer type and status are enumerations stored
as strings, which keeps the schema readable in the H2 console and avoids a lookup-table
join for data that never changes at runtime.

```
┌──────────────────────────── RETENTION_OFFER ────────────────────────────┐
│ PK  id                  BIGINT        identity                          │
│ UQ  case_ref            VARCHAR(16)   CRO-0001 .. CRO-9999              │
├─ customer ──────────────────────────────────────────────────────────────┤
│     customer_name       VARCHAR(120)  NOT NULL                          │
│     customer_email      VARCHAR(160)  NOT NULL, @Email                  │
│     account_id          VARCHAR(24)   NOT NULL, [A-Za-z0-9-]{4,24}      │
│     segment             VARCHAR(20)   NOT NULL  ──▶ enum Segment        │
│     churn_risk          INTEGER       NOT NULL, 0..100                  │
│     monthly_value       DECIMAL(11,2) NOT NULL, > 0                     │
├─ offer ─────────────────────────────────────────────────────────────────┤
│     offer_type          VARCHAR(24)   NOT NULL  ──▶ enum OfferType      │
│     offer_value         DECIMAL(11,2) NOT NULL, > 0                     │
│     notes               VARCHAR(1000)                                   │
├─ workflow ──────────────────────────────────────────────────────────────┤
│     status              VARCHAR(24)   NOT NULL  ──▶ enum OfferStatus    │
├─ audit ─────────────────────────────────────────────────────────────────┤
│     created_by          VARCHAR(60)   NOT NULL                          │
│     created_at          TIMESTAMP     NOT NULL                          │
│     updated_at          TIMESTAMP     NOT NULL                          │
│     submitted_by/_at    VARCHAR(60) / TIMESTAMP                         │
│     decision_by/_at     VARCHAR(60) / TIMESTAMP                         │
│     decision_comment    VARCHAR(500)                                    │
│     sent_by/_at         VARCHAR(60) / TIMESTAMP                         │
│     outcome_at          TIMESTAMP                                       │
└─────────────────────────────────────────────────────────────────────────┘
   INDEX idx_offer_status (status)        — dashboard counts, approvals queue
   INDEX idx_offer_customer (customerName) — search

Enumerations
  Segment     : BASIC · PLUS · PREMIUM · ENTERPRISE
  OfferType   : DISCOUNT_PERCENT · FREE_MONTHS · PLAN_UPGRADE · LOYALTY_CREDIT
  OfferStatus : DRAFT · PENDING_APPROVAL · APPROVED · REJECTED · SENT · ACCEPTED · DECLINED

Derived (not stored, computed for the UI)
  riskBand              : Low <40 · Medium 40-59 · High 60-79 · Critical >=80
  retainedMonthlyValue  : monthlyValue - amortised monthly cost of the incentive
  editable              : status in (DRAFT, REJECTED)
```

### 4.2 Users
Authentication uses an in-memory store (constraint C1). There is no `users` table.

| Username | Password | Roles |
|---|---|---|
| `agent` | `agent123` | `ROLE_AGENT` |
| `manager` | `manager123` | `ROLE_MANAGER` |
| `admin` | `admin123` | `ROLE_ADMIN`, `ROLE_AGENT`, `ROLE_MANAGER` |

> Local demo instance, synthetic data only (constraints C2, C5).

### 4.3 Status workflow — the state machine

```
                       ┌──────────────── revise (AGENT) ─────────────┐
                       ▼                                             │
                  ┌─────────┐   submit (AGENT)    ┌──────────────────┴─┐
      create ────▶│  DRAFT  │────────────────────▶│  PENDING_APPROVAL  │
      (AGENT)     └─────────┘                     └─────────┬──────────┘
                                                            │
                                      approve (MANAGER)     │     reject (MANAGER)
                                     ┌──────────────────────┴────────────────┐
                                     ▼                                       ▼
                               ┌──────────┐                            ┌──────────┐
                               │ APPROVED │                            │ REJECTED │
                               └────┬─────┘                            └──────────┘
                                    │ send (AGENT)
                                    ▼
                               ┌──────────┐
                               │   SENT   │
                               └────┬─────┘
                   outcome (AGENT)  │
                 ┌──────────────────┴───────────────────┐
                 ▼                                      ▼
           ┌──────────┐                           ┌──────────┐
           │ ACCEPTED │ (terminal)                │ DECLINED │ (terminal)
           └──────────┘                           └──────────┘
```

| From | Action | To | Role |
|---|---|---|---|
| `DRAFT` | submit | `PENDING_APPROVAL` | AGENT |
| `PENDING_APPROVAL` | approve | `APPROVED` | MANAGER |
| `PENDING_APPROVAL` | reject | `REJECTED` | MANAGER |
| `REJECTED` | revise | `DRAFT` | AGENT |
| `APPROVED` | send | `SENT` | AGENT |
| `SENT` | outcome: accepted | `ACCEPTED` | AGENT |
| `SENT` | outcome: declined | `DECLINED` | AGENT |

**Every other combination is refused.** `SENT` is reachable only from `APPROVED` — this
is the rule that implements objective B1 and success criterion SC1.

---

## 5. API / Endpoint List

All endpoints are server-rendered HTML except `/actuator/*`, which returns JSON.

| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/` | authenticated | Redirect to `/dashboard` |
| GET | `/login` | public | Login form |
| POST | `/login` | public | Authenticate, create session |
| POST | `/logout` | authenticated | Invalidate session |
| GET | `/dashboard` | authenticated | Summary dashboard (M9) |
| GET | `/offers` | authenticated | List + search + filter + paginate (M3, M6) |
| GET | `/offers/new` | AGENT | Create form (M2) |
| POST | `/offers` | AGENT | Create offer (M2) |
| GET | `/offers/{id}` | authenticated | Offer detail + audit trail (M4) |
| GET | `/offers/{id}/edit` | AGENT | Edit form, draft/rejected only (M5) |
| POST | `/offers/{id}` | AGENT | Update offer (M5) |
| POST | `/offers/{id}/submit` | AGENT | `DRAFT → PENDING_APPROVAL` (M7) |
| POST | `/offers/{id}/approve` | MANAGER | `PENDING_APPROVAL → APPROVED` (M8) |
| POST | `/offers/{id}/reject` | MANAGER | `PENDING_APPROVAL → REJECTED` (M8) |
| POST | `/offers/{id}/revise` | AGENT | `REJECTED → DRAFT` (M7) |
| POST | `/offers/{id}/send` | AGENT | `APPROVED → SENT` (M7) |
| POST | `/offers/{id}/outcome` | AGENT | `SENT → ACCEPTED \| DECLINED` (M7) |
| GET | `/actuator/health` | public | Deployment and Ansible probe (M12, M15) |
| GET | `/actuator/info` | public | Build and version information |

### Query parameters on `GET /offers`

| Parameter | Type | Meaning |
|---|---|---|
| `q` | string | Free text across case reference, customer name, customer email |
| `status` | `OfferStatus` | Exact status filter |
| `segment` | `Segment` | Exact segment filter |
| `minRisk` | integer 0–100 | Churn risk greater than or equal to |
| `page` | integer | Zero-based page index |
| `size` | integer | Page size, default 10 |

---

## 6. Working Local Setup

### 6.1 Prerequisites verified on this machine

| Tool | Version found |
|---|---|
| JDK | 17.0.20.1 (pinned via `scripts/env.sh`) |
| Apache Maven | 3.10.0 |
| Git | 2.50.1 |
| Docker Engine | 29.8.2 |
| Jenkins LTS | installed via Homebrew |
| Apache Tomcat | 11 |
| Nginx | 1.31.7 |
| Ansible | core 2.21.5 |
| Google Chrome | 155 (Selenium target) |

### 6.2 Project layout

```
devops_project_/
├── pom.xml                     Maven build, WAR packaging, finalName=crop
├── .mvn/maven.config           -B --no-transfer-progress (clean CI logs)
├── scripts/
│   ├── env.sh                  pins JAVA_HOME to JDK 17 + the port map
│   ├── run-local.sh            mvn spring-boot:run on :8070
│   └── stop-local.sh           free a port
├── src/main/java/com/crop/retention/
│   ├── RetentionApplication.java      Spring Boot entry point
│   ├── ServletInitializer.java        WAR bootstrap for external Tomcat
│   ├── config/SecurityConfig.java     form login, BCrypt, role rules
│   ├── model/                         RetentionOffer, OfferStatus, Segment, OfferType
│   ├── repository/                    RetentionOfferRepository (JPA + search query)
│   ├── service/                       OfferService, OfferWorkflow  (Sprint 2)
│   └── web/                           controllers + GlobalModelAdvice
├── src/main/resources/
│   ├── application.properties  port, datasource, actuator, logging
│   ├── templates/              Thymeleaf: layout, login, dashboard, offers/*
│   └── static/css/app.css      the whole design system, no external assets
├── src/test/java/              unit tests (Sprint 2) + Selenium ITs (Task 9)
└── docs/                       one document and screenshots per task
```

### 6.3 Build and run

```bash
# pin the JDK and the port map for this shell
. scripts/env.sh

# build the artefact
mvn clean package                 # -> target/crop.war

# run it
./scripts/run-local.sh            # http://localhost:8070
# or, from the built artefact exactly as the container does:
APP_PORT=8070 java -jar target/crop.war
```

### 6.4 Verification performed

```
$ curl -s http://localhost:8070/actuator/health
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"H2", ...

$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8070/login
200
```

Startup log:
```
Tomcat started on port 8070 (http) with context path '/'
Started RetentionApplication in 2.47 seconds (process running for 2.63)
```

### 6.5 Issues encountered and resolved during setup

| Issue | Diagnosis | Fix |
|---|---|---|
| `Non-resolvable parent POM … in offline mode` | Maven was invoked with `-o` before the dependency tree had ever been downloaded | First build run online to populate `~/.m2` |
| `Feature not supported: "AUTO_SERVER=TRUE && DB_CLOSE_ON_EXIT=FALSE"` | H2 2.x removed those JDBC URL flags; Hibernate then could not determine a dialect and the context failed to start | Simplified the URL to `jdbc:h2:file:${CROP_DATA_DIR:./data}/retention` |
| Maven ran on JDK 27 while the project targets 17 | Homebrew Maven defaults to its own `openjdk` | `scripts/env.sh` pins `JAVA_HOME` to JDK 17; the `Jenkinsfile` does the same (risk R4) |
| Noisy download progress in build logs | Maven default transfer listener | `.mvn/maven.config` with `-B --no-transfer-progress` |

---

## Deliverable checklist for Task 3

- [x] SRS summary — §1 (18 functional, 10 non-functional requirements)
- [x] Use-case diagram and a worked use-case specification — §2
- [x] Architecture diagram (layered + deployment) — §3
- [x] Technology selection with rationale — §3.4
- [x] Data model with ERD and state machine — §4
- [x] API / endpoint list — §5
- [x] Working local setup, verified — §6
- [x] Evidence screenshots — `task-03-architecture.png`, `task-03-login.png`, `task-03-app-shell.png`
