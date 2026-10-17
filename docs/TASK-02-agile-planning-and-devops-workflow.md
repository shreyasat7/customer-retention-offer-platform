# Task 2 — Agile Planning and DevOps Workflow

**Project:** Automated Customer Retention Offer Platform (CROP)
**Method:** Scrum-of-one with a Kanban task board (WIP limit 2)
**Sprint length:** 5 working days · **Sprints:** 2 · **Velocity assumption:** ~26 story points/sprint

---

## 1. Personas

| Persona | Role in system | Goal |
|---|---|---|
| **Asha — Retention Agent** | `ROLE_AGENT` | Raise an offer for an at-risk customer in under a minute and know exactly where it stands |
| **Manav — Retention Manager** | `ROLE_MANAGER` | Approve or reject offers quickly without losing control of discount budget |
| **Dev — Administrator** | `ROLE_ADMIN` | Keep the platform running, verify the audit trail, operate the pipeline |

---

## 2. Product Backlog — 15 items with user stories and acceptance criteria

Estimates use the Fibonacci scale (story points). Priority: **MH** = Must Have, **SH** = Should Have.

---

### US-01 · Role-based login and logout
> **As** any CROP user **I want** to sign in with my own account **so that** my actions are attributed to me and I only see the controls my role allows.

**Priority** MH · **Points** 5 · **Sprint** 1 · **MVP item** M1

**Acceptance criteria**
- **AC1** Given valid credentials for `agent`, `manager` or `admin`, when I submit the login form, then I land on the dashboard and my username and role are shown in the header.
- **AC2** Given invalid credentials, when I submit, then I stay on the login page and see "Invalid username or password" — and no record of which field was wrong.
- **AC3** Given I am not authenticated, when I request any `/offers/**` or `/dashboard` URL, then I am redirected to `/login`.
- **AC4** Given I am signed in, when I click **Sign out**, then my session is invalidated and I return to the login page.

---

### US-02 · Create a retention offer
> **As** a Retention Agent **I want** to create a retention offer for an at-risk customer **so that** the save attempt is recorded in one system instead of my spreadsheet.

**Priority** MH · **Points** 5 · **Sprint** 1 · **MVP item** M2

**Acceptance criteria**
- **AC1** Given I am an Agent, when I open **New Offer**, then I see fields for customer name, email, account ID, segment, churn risk score, monthly value, offer type, offer value and notes.
- **AC2** Given I submit a valid form, then a record is created with status **DRAFT**, a generated case reference `CRO-####`, and `createdBy` set to my username.
- **AC3** Given I submit with customer name blank, an invalid email, a risk score outside 0–100, or a negative monthly value, then the form redisplays with a field-level error and nothing is saved.
- **AC4** Given a Manager-only account, when I open `/offers/new`, then I receive HTTP 403.

---

### US-03 · View the offer list
> **As** any user **I want** a paginated list of all offers **so that** I can see the team's whole pipeline at a glance.

**Priority** MH · **Points** 3 · **Sprint** 1 · **MVP item** M3

**Acceptance criteria**
- **AC1** Given offers exist, when I open **Offers**, then I see case reference, customer, segment, risk, offer, value, status and last-updated date in a table.
- **AC2** Given more than 10 offers match, then results are paginated 10 per page with working Previous/Next controls and a visible result count.
- **AC3** Each row's status is rendered as a colour-coded pill matching its workflow state.
- **AC4** Given no offers match, then an empty state is shown instead of a blank table.

---

### US-04 · View offer detail with audit trail
> **As** a Manager **I want** to open a single offer and see its full history **so that** I can make and justify an approval decision.

**Priority** MH · **Points** 3 · **Sprint** 1 · **MVP item** M4

**Acceptance criteria**
- **AC1** Given an offer exists, when I open it, then I see every field plus `createdBy`/`createdAt`, `decisionBy`/`decisionAt`/`decisionComment` and `sentAt`.
- **AC2** The detail page shows only the workflow action buttons that my role may perform in the offer's current status.
- **AC3** Given an unknown id, then a 404 "offer not found" page is shown rather than a stack trace.

---

### US-05 · Update an offer in Draft
> **As** an Agent **I want** to correct an offer while it is still a draft **so that** I can fix mistakes before a Manager reviews it.

**Priority** MH · **Points** 3 · **Sprint** 2 · **MVP item** M5

**Acceptance criteria**
- **AC1** Given an offer in **DRAFT** or **REJECTED**, when I click **Edit** and save, then the changes persist and `updatedAt` is refreshed.
- **AC2** Given an offer in **PENDING_APPROVAL**, **APPROVED**, **SENT**, **ACCEPTED** or **DECLINED**, then the **Edit** button is hidden and a direct POST to the edit URL is rejected with a business-rule error.
- **AC3** Validation rules from US-02 apply identically on edit.

---

### US-06 · Search and filter offers
> **As** an Agent **I want** to search by customer and filter by status, segment and risk **so that** I do not send a second offer to a customer who already has one.

**Priority** MH · **Points** 5 · **Sprint** 2 · **MVP item** M6

**Acceptance criteria**
- **AC1** Given a free-text query, when I search, then offers whose case reference, customer name or email contain the query (case-insensitive) are returned.
- **AC2** Status, segment and minimum-risk filters can be combined with the text query; all criteria apply together (AND).
- **AC3** Active filters are visible and clearable with one **Reset** control.
- **AC4** Filters survive pagination — moving to page 2 keeps the same criteria.

---

### US-07 · Role-based status workflow
> **As** the business **I want** offer status transitions enforced by role **so that** no discount can reach a customer without a Manager's approval.

**Priority** MH · **Points** 8 · **Sprint** 2 · **MVP item** M7

**Acceptance criteria**
- **AC1** The permitted transitions are exactly: `DRAFT→PENDING_APPROVAL` (Agent), `PENDING_APPROVAL→APPROVED|REJECTED` (Manager), `APPROVED→SENT` (Agent), `SENT→ACCEPTED|DECLINED` (Agent), `REJECTED→DRAFT` (Agent).
- **AC2** Any other transition is refused with a business-rule error and the record is unchanged.
- **AC3** An Agent attempting to approve receives HTTP 403; a Manager attempting to send receives HTTP 403.
- **AC4** `SENT` is unreachable from any status other than `APPROVED` — covered by an automated unit test (success criterion SC1).
- **AC5** Every transition stamps the acting username and timestamp onto the record.

---

### US-08 · Manager approve / reject with comment
> **As** a Manager **I want** to approve or reject with a written reason **so that** Finance and Audit can see why a discount was granted.

**Priority** MH · **Points** 3 · **Sprint** 2 · **MVP item** M8

**Acceptance criteria**
- **AC1** Given an offer in **PENDING_APPROVAL**, I see **Approve** and **Reject** with a decision-comment box.
- **AC2** Rejection requires a non-empty comment; approval records a comment if supplied.
- **AC3** After a decision the offer shows the new status, my username and the decision timestamp.
- **AC4** A rejected offer can be revised by an Agent, returning it to **DRAFT**.

---

### US-09 · Summary dashboard
> **As** a Manager **I want** a dashboard of retention KPIs **so that** I can report performance without building a pivot table.

**Priority** MH · **Points** 8 · **Sprint** 2 · **MVP item** M9

**Acceptance criteria**
- **AC1** The dashboard shows total offers, offers awaiting approval, acceptance rate (%) and monthly revenue retained.
- **AC2** A status funnel shows the count in each of the 7 workflow states.
- **AC3** A churn-risk breakdown groups offers into Low / Medium / High / Critical bands.
- **AC4** A "needs attention" panel lists the oldest offers still awaiting approval.
- **AC5** The dashboard is the landing page after login and renders in under 2 seconds (SC4).

---

### US-10 · Version-controlled codebase with branch policy
> **As** the DevOps engineer **I want** a GitHub repository with an agreed branching model and reviewed pull requests **so that** no unreviewed code reaches the release branch.

**Priority** MH · **Points** 3 · **Sprint** 1 · **MVP item** M10

**Acceptance criteria**
- **AC1** Repository contains README, `.gitignore`, issue templates and documented branch naming rules.
- **AC2** Work happens on `feature/*` branches merged into `develop` through a pull request carrying review comments.
- **AC3** A merge conflict is created and resolved deliberately, with evidence.
- **AC4** The release baseline is tagged `v1.0.0` on `main`.

---

### US-11 · Continuous integration build
> **As** the DevOps engineer **I want** Jenkins to build the project automatically on every commit **so that** a broken build is detected within minutes.

**Priority** MH · **Points** 5 · **Sprint** 1 · **MVP item** M11

**Acceptance criteria**
- **AC1** A Jenkins job is connected to the GitHub repository and triggered by SCM polling.
- **AC2** The job runs `mvn clean package` and finishes **SUCCESS** on a healthy commit.
- **AC3** The built WAR is archived as a Jenkins build artefact and downloadable from the build page.
- **AC4** A commit pushed to the tracked branch produces a new build without any manual click (SC6).

---

### US-12 · Pipeline as code with server deployment
> **As** the DevOps engineer **I want** the pipeline defined in a `Jenkinsfile` **so that** the delivery process is versioned with the code.

**Priority** MH · **Points** 5 · **Sprint** 2 · **MVP item** M12

**Acceptance criteria**
- **AC1** A declarative `Jenkinsfile` in the repository root defines Checkout → Build → Test → Package → Deploy stages.
- **AC2** At least one environment setting (deploy target port / Tomcat path) is a pipeline parameter, not a hard-coded value.
- **AC3** The pipeline deploys the WAR to Tomcat and the deployed URL responds HTTP 200.
- **AC4** The Jenkins stage view shows every stage green.

---

### US-13 · Selenium regression suite as a deployment gate
> **As** the business **I want** automated UI tests to run before deployment **so that** a regression never reaches the server.

**Priority** MH · **Points** 8 · **Sprint** 2 · **MVP item** M13

**Acceptance criteria**
- **AC1** At least four critical user journeys are automated with Selenium WebDriver and meaningful assertions.
- **AC2** The suite runs locally through Maven and in Jenkins headlessly.
- **AC3** A failing test captures a screenshot to the build workspace.
- **AC4** Test results are published in Jenkins and a failing test marks the build **FAILURE** and **stops the deploy stage** (SC8).
- **AC5** A deliberately introduced defect is caught, fixed in a commit, and the rerun pipeline is green.

---

### US-14 · Containerised, registry-published, auto-deployed image
> **As** the DevOps engineer **I want** the application packaged as a versioned Docker image and deployed as a container automatically **so that** the runtime is identical everywhere.

**Priority** MH · **Points** 8 · **Sprint** 2 · **MVP item** M14

**Acceptance criteria**
- **AC1** A `Dockerfile` builds the application image; the full container lifecycle (build, tag, run, logs, inspect, stop, restart, remove) is documented.
- **AC2** The pipeline tags the image with the Jenkins build number **and** `latest`.
- **AC3** The image is pushed to a registry and visible through the registry's catalog interface.
- **AC4** After tests pass, a fresh container replaces the previous one and serves the application (commit-to-container).

---

### US-15 · Idempotent provisioning with health check and rollback
> **As** the DevOps engineer **I want** the target node configured by an Ansible playbook **so that** the environment is reproducible and recoverable.

**Priority** MH · **Points** 8 · **Sprint** 2 · **MVP item** M15

**Acceptance criteria**
- **AC1** A documented configuration specification lists required packages, users, directories, files, ports and services.
- **AC2** An Ansible inventory and YAML playbook provision a clean environment and deploy the container.
- **AC3** A second run of the same playbook reports **changed=0** (SC9).
- **AC4** A health check confirms the application responds after provisioning.
- **AC5** Rollback to the previous image version is demonstrated and the application recovers (SC10).

---

## 3. Backlog Summary

| ID | Title | Persona | Pts | Priority | Sprint | MVP |
|---|---|---|---|---|---|---|
| US-01 | Role-based login and logout | All | 5 | MH | 1 | M1 |
| US-02 | Create a retention offer | Agent | 5 | MH | 1 | M2 |
| US-03 | View the offer list | All | 3 | MH | 1 | M3 |
| US-04 | View offer detail with audit trail | Manager | 3 | MH | 1 | M4 |
| US-10 | Version control + branch policy | DevOps | 3 | MH | 1 | M10 |
| US-11 | Continuous integration build | DevOps | 5 | MH | 1 | M11 |
| US-05 | Update an offer in Draft | Agent | 3 | MH | 2 | M5 |
| US-06 | Search and filter offers | Agent | 5 | MH | 2 | M6 |
| US-07 | Role-based status workflow | Business | 8 | MH | 2 | M7 |
| US-08 | Approve / reject with comment | Manager | 3 | MH | 2 | M8 |
| US-09 | Summary dashboard | Manager | 8 | MH | 2 | M9 |
| US-12 | Pipeline as code + deploy | DevOps | 5 | MH | 2 | M12 |
| US-13 | Selenium deployment gate | Business | 8 | MH | 2 | M13 |
| US-14 | Docker image + auto-deploy | DevOps | 8 | MH | 2 | M14 |
| US-15 | Ansible provisioning + rollback | DevOps | 8 | MH | 2 | M15 |
| | **Total** | | **80** | | | |

---

## 4. Sprint Plan

### Sprint 1 — "Walking skeleton and the build pipeline" (24 points)
**Sprint goal:** a running, secured application that can create and read offers, in version control, built automatically by Jenkins on every commit.

| Day | Focus | Items |
|---|---|---|
| 1 | Architecture, tech setup, local run | Task 3 |
| 2 | Repository, skeleton, issues, branch policy | US-10 |
| 3 | Login + RBAC, offer list | US-01, US-03 |
| 4 | Create offer, detail page (feature branch + PR) | US-02, US-04 |
| 5 | Jenkins install, CI job, archived artefact | US-11 |

**Sprint 1 review outcome:** demo of login → create → view, plus a Jenkins build triggered by a push.

### Sprint 2 — "Complete the MVP and automate delivery end-to-end" (56 points)
**Sprint goal:** full MVP functionality, release-tagged, delivered automatically from commit to a running container on a provisioned node, gated by Selenium.

| Day | Focus | Items |
|---|---|---|
| 1 | Edit, search/filter, workflow, approvals, dashboard | US-05, US-06, US-07, US-08, US-09 |
| 2 | Conflict resolution, merge to main, tag `v1.0.0` | US-10 |
| 3 | `Jenkinsfile`, Tomcat deploy, Selenium suite | US-12, US-13 |
| 4 | Continuous testing gate, Docker image, registry, auto-deploy | US-13, US-14 |
| 5 | Ansible provisioning, idempotency, health check, rollback | US-15 |

**Sprint 2 review outcome:** push a commit and watch it reach a running container with tests gating the deploy; re-run Ansible to show `changed=0`; roll back.

---

## 5. Kanban Task Board — 15 tasks

**Columns:** Backlog → Ready → In Progress (WIP ≤ 2) → In Review → Testing → Done
**Policies**
- A card enters **Ready** only when its acceptance criteria are written.
- Only two cards may sit in **In Progress** at once.
- A card enters **In Review** only with an open pull request.
- A card enters **Testing** only when it is merged into `develop` and the Jenkins build is green.
- A card reaches **Done** only when every Definition-of-Done item is satisfied.

| Card | Title | Column — Sprint 2 / Day 3 snapshot | Column at MVP close | Blocked by |
|---|---|---|---|---|
| T1 | US-01 Login and RBAC | Done | Done | — |
| T2 | US-02 Create offer | Done | Done | T1 |
| T3 | US-03 Offer list | Done | Done | T1 |
| T4 | US-04 Offer detail + audit | Done | Done | T2 |
| T5 | US-05 Edit draft offer | Done | Done | T2 |
| T6 | US-06 Search and filter | Done | Done | T3 |
| T7 | US-07 Status workflow engine | Done | Done | T4 |
| T8 | US-08 Approve / reject | Testing | Done | T7 |
| T9 | US-09 Summary dashboard | Testing | Done | T7 |
| T10 | US-10 Repo, branches, PR, tag | Done | Done | — |
| T11 | US-11 Jenkins CI job | Done | Done | T10 |
| T12 | US-12 Jenkinsfile + Tomcat deploy | In Review | Done | T11 |
| T13 | US-13 Selenium gate | In Progress | Done | T12 |
| T14 | US-14 Docker image + registry + auto-deploy | In Progress | Done | T13 |
| T15 | US-15 Ansible provisioning + rollback | Ready | Done | T14 |

> The *snapshot* column is the state captured in the board screenshot
> (`docs/screenshots/task-02-board-and-workflow.png`), taken mid-Sprint-2 so that the WIP
> limit and the review/testing columns are visible in use. The *MVP close* column is the
> final state once all 15 cards satisfy the Definition of Done.

---

## 6. Definition of Ready

A card may be pulled into **In Progress** only when all of the following are true:

1. The user story is written in role–goal–benefit form.
2. Acceptance criteria are written and testable.
3. The card is estimated in story points.
4. Dependencies are identified and not blocking.
5. The card traces to an MVP item (M1–M15).

---

## 7. Definition of Done

A card is **Done** only when **every** item below is satisfied:

### Code
1. Code implements all acceptance criteria of the story.
2. Code is written on a `feature/*` branch, never directly on `develop` or `main`.
3. Commit messages follow the convention `type(scope): subject`.
4. No commented-out code, no debug printing, no hard-coded secrets.

### Review and integration
5. A pull request is opened and carries at least one substantive review comment.
6. The pull request is merged into `develop` with no unresolved conflicts.

### Build and test
7. `mvn clean package` succeeds locally.
8. Unit tests for business rules pass; workflow rules have explicit tests.
9. The Jenkins build for the merge commit is **SUCCESS**.
10. The Selenium regression suite is green (from Task 10 onward).

### Deployment
11. The artefact is archived by Jenkins.
12. The change is deployed by the pipeline to the target (Tomcat / container) without manual steps.
13. A health check on the deployed URL returns HTTP 200.

### Documentation
14. Documentation for the related task is updated under `docs/`.
15. Screenshot evidence is captured under `docs/screenshots/`.

---

## 8. DevOps Lifecycle Workflow — Development to Operations

```
        ┌──────────────────────── FEEDBACK LOOP ─────────────────────────┐
        │                                                                 │
        ▼                                                                 │
  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┴───┐
  │  1 PLAN  │──▶│  2 CODE  │──▶│ 3 BUILD  │──▶│ 4 TEST   │──▶│ 5 RELEASE    │
  │ backlog  │   │ feature/ │   │ Jenkins  │   │ JUnit +  │   │ tag v1.0.0   │
  │ Kanban   │   │ branch,  │   │ mvn      │   │ Selenium │   │ versioned    │
  │ stories  │   │ PR review│   │ package  │   │   GATE   │   │ Docker image │
  └──────────┘   └──────────┘   └──────────┘   └────┬─────┘   └──────┬───────┘
                                                     │ fail           │
                                                     ▼                ▼
                                              build FAILURE   ┌──────────────┐
                                              deploy BLOCKED  │  6 DEPLOY    │
                                                              │ Tomcat WAR + │
                                                              │ container    │
                                                              └──────┬───────┘
                                                                     ▼
                                   ┌──────────────┐          ┌──────────────┐
                                   │  8 MONITOR   │◀─────────│  7 OPERATE   │
                                   │ health check │          │ Ansible      │
                                   │ docker logs  │          │ idempotent   │
                                   │ /actuator    │          │ provisioning │
                                   └──────┬───────┘          │ + rollback   │
                                          │                  └──────────────┘
                                          └──▶ defects feed back into PLAN
```

### Stage-by-stage mapping to this project

| # | Stage | Tool | What happens | Project task |
|---|---|---|---|---|
| 1 | **Plan** | Markdown backlog, Kanban board, GitHub Issues | Stories written, acceptance criteria agreed, cards pulled under WIP limit 2 | Tasks 1–2 |
| 2 | **Code** | Git, GitHub, feature branches, pull requests | Developer commits to `feature/*`, opens PR, review comments, merge to `develop` | Tasks 4–6 |
| 3 | **Build** | Jenkins + Maven | SCM polling detects the commit, `mvn clean package` produces `crop.war`, artefact archived | Tasks 7–8 |
| 4 | **Test** | JUnit + Selenium WebDriver (headless Chrome) | Unit tests on workflow rules, then Selenium journeys against a live instance. **Failure stops the pipeline before deploy.** | Tasks 9–10 |
| 5 | **Release** | Git tag + Docker image tag + registry | `v1.0.0` tagged; image built as `crop:<build-number>` and `crop:latest`, pushed to the registry | Tasks 6, 12 |
| 6 | **Deploy** | Tomcat (WAR) and Docker (container) | Pipeline copies the WAR to Tomcat and replaces the running container from the registry image | Tasks 8, 12 |
| 7 | **Operate** | Ansible | Target node provisioned declaratively: packages, user, directories, ports, services. Re-runnable with no side effects | Tasks 13–14 |
| 8 | **Monitor** | Health endpoint, `docker logs`, Jenkins trend graphs | Post-deploy HTTP health check; failure triggers rollback to the previous image | Task 14 |

### Branching model

```
main      ────●───────────────────────────────●──────▶   release-only, tagged v1.0.0
               \                             /
develop   ──────●────●────────●────●────●───●─────────▶   integration branch
                 \    \        \    \    /
feature/          ●────●        ●────●──●                 short-lived, one story each
```

| Branch | Purpose | Merge rule |
|---|---|---|
| `main` | Release-ready baseline only | Only from `develop`, tagged on merge |
| `develop` | Integration of completed stories | Only from `feature/*` via pull request |
| `feature/<story>` | One user story | Rebased/merged from `develop` before PR |
| `bugfix/<issue>` | Defect correction | Same rules as `feature/*` |

---

## Deliverable checklist for Task 2

- [x] Product backlog with 15 items — §2, §3
- [x] User stories with acceptance criteria — §2
- [x] 15-task Kanban/Scrum task board — §5
- [x] Sprint plan (2 sprints) — §4
- [x] Definition of Ready and Definition of Done — §6, §7
- [x] DevOps lifecycle workflow diagram, development → operations — §8
- [x] Branching model — §8
- [x] Evidence screenshot — `docs/screenshots/task-02-board-and-workflow.png`
