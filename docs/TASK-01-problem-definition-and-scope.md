# Task 1 — Problem Definition and Scope

**Project:** Automated Customer Retention Offer Platform (CROP)
**Date:** 2026-10-17
**Author:** Shreya Sathish
**Status:** Scope frozen

---

## 1. Real-Time Need

Subscription businesses (telecom, OTT, SaaS, insurance, broadband) lose 15–30% of their
customer base every year. Industry benchmarks put the cost of acquiring a new customer at
**5–7× the cost of retaining an existing one**, so a "save" is worth far more than a new sale.

Today most mid-sized companies run retention as a **manual, spreadsheet-driven process**:

1. An analytics team exports a monthly churn-risk list to Excel.
2. Retention agents call customers and improvise a discount on the phone.
3. Discounts above a threshold need a manager's verbal or email approval.
4. Outcomes are recorded inconsistently, so nobody can prove which offers actually worked.

The consequences are measurable and well documented in retention operations:

| Problem | Business impact |
|---|---|
| No approval trail for discounts | Margin leakage; agents over-discount to hit save targets |
| Offers tracked in personal spreadsheets | Duplicate offers to the same customer; no single source of truth |
| No status workflow | Offers approved but never actually sent to the customer |
| Outcomes not recorded against the offer | Impossible to calculate true acceptance rate or saved revenue |
| No audit log | Compliance and finance cannot verify who approved what |

**Need:** a single, lightweight, auditable web platform where a retention agent raises an
offer, a manager approves it, and the outcome is recorded against the same record — with a
dashboard that shows acceptance rate and revenue saved.

---

## 2. Problem Statement

> Retention offers are currently created, approved and tracked across disconnected
> spreadsheets and email threads. There is no enforced approval workflow, no shared record of
> an offer's state, and no reliable measurement of which offers retain customers. As a result,
> discount margin leaks, duplicate offers reach the same customer, approved offers are never
> sent, and management has no trustworthy view of retention performance.
>
> **CROP** solves this by providing a role-based web application in which every retention
> offer moves through an enforced status workflow (Draft → Pending Approval → Approved →
> Sent → Accepted/Declined), is searchable by any team member, and is aggregated into a live
> summary dashboard. The entire application is built, tested, containerised and deployed
> through an automated DevOps pipeline so that changes reach the server without manual steps.

---

## 3. Target Users

| User | Who they are | What they do in CROP |
|---|---|---|
| **Retention Agent** | Front-line contact-centre staff handling at-risk customers | Creates offer records, submits them for approval, sends approved offers, records the customer's response |
| **Retention Manager** | Team lead who owns discount budget and margin | Reviews pending offers, approves or rejects with a comment, monitors the dashboard |
| **Administrator** | IT / operations owner of the platform | Full access to all records, verifies the audit trail, operates the deployment |

### Secondary (non-login) consumers
- **Finance** — consumes "revenue retained" figures from the dashboard.
- **Compliance/Audit** — needs the who-approved-what trail.

---

## 4. Stakeholder List

| # | Stakeholder | Interest / stake | Influence |
|---|---|---|---|
| S1 | Head of Customer Retention | Lower churn, provable save rate | High — project sponsor |
| S2 | Retention Agents (end users) | Faster, less error-prone than spreadsheets | High — daily users; adoption risk |
| S3 | Retention Manager | Control over discount spend | High — approval authority is the core feature |
| S4 | Finance Controller | Accurate discount cost and retained revenue | Medium |
| S5 | IT / DevOps team | Maintainable, automatically deployable system | High — owns the pipeline |
| S6 | Compliance & Audit | Immutable record of approvals | Medium |
| S7 | Data/Analytics team | Supplies the churn-risk score consumed by the platform | Medium |
| S8 | Customers (indirect) | Receive a fair, timely retention offer | Low direct influence, high importance |

---

## 5. Existing Pain Points (As-Is)

| ID | Pain point | Current workaround | Severity |
|---|---|---|---|
| P1 | No enforced approval before a discount is given | Verbal / email approval | **Critical** |
| P2 | Offer data lives in per-agent spreadsheets | Weekly manual consolidation | **Critical** |
| P3 | Offer status is unknown (approved? sent?) | Ask the agent | High |
| P4 | Customer outcome not linked to the offer | Separate CRM note | High |
| P5 | No search across offers → duplicate offers | Ctrl+F in Excel | High |
| P6 | No aggregated KPIs for management | Manual monthly pivot table | Medium |
| P7 | Deployment of any internal tool takes days | Manual file copy by IT | Medium |

---

## 6. Objectives

### Business objectives
| ID | Objective |
|---|---|
| B1 | Enforce manager approval on every retention offer before it reaches a customer |
| B2 | Provide one searchable system of record for all retention offers |
| B3 | Measure acceptance rate and retained revenue automatically |
| B4 | Keep a complete audit trail of who created, approved and sent each offer |

### Technical / DevOps objectives
| ID | Objective |
|---|---|
| T1 | Version-controlled codebase with a branching policy and reviewed pull requests |
| T2 | Automated Maven build triggered by a commit (continuous integration) |
| T3 | Automated UI regression testing with Selenium that blocks a bad deployment |
| T4 | Containerised, versioned, registry-published application image |
| T5 | Repeatable, idempotent server provisioning through configuration management |
| T6 | Demonstrable rollback to the previous stable release |

---

## 7. Constraints

| ID | Constraint | Consequence on design |
|---|---|---|
| C1 | Single-developer academic project, ~2 week duration | Scope frozen at 15 backlog items; no microservices |
| C2 | Must run entirely on one macOS laptop (no cloud spend) | Local Jenkins, local Docker registry, localhost deployment targets |
| C3 | Must demonstrate the prescribed toolchain (Git, Jenkins, Maven, Selenium, Docker, Ansible) | Technology choices driven by tool compatibility, e.g. Maven + WAR + Tomcat |
| C4 | No external database licence | Embedded H2 file database |
| C5 | No real customer data (privacy) | Seeded synthetic demo data only |
| C6 | No production email gateway | "Send offer" transitions status and logs; it does not send real email |
| C7 | Must be demonstrable live in a ~20 minute viva | Every tool must have a visible interface and a one-command start script |
| C8 | Browser-based UI only; no native mobile app | Responsive web UI |

### Assumptions
- Churn-risk score is supplied by an upstream analytics team and entered/imported with the offer; CROP does not compute ML predictions.
- User accounts are provisioned by the administrator; no self-registration.
- Single-currency (INR) amounts.

---

## 8. Measurable Success Criteria

### Product success criteria
| ID | Criterion | Target | How measured |
|---|---|---|---|
| SC1 | Offers that reach a customer without manager approval | **0** | Workflow blocks `SENT` unless status was `APPROVED`; verified by unit test |
| SC2 | Time to create and submit an offer | **< 60 s** | Manual timing of the create form |
| SC3 | Any offer findable by customer name, email or case reference | **100%** | Search test on seeded data |
| SC4 | Dashboard KPIs (acceptance rate, retained value) | Available **on login, < 2 s** | Page load timing |
| SC5 | Audit fields (created-by, approved-by, timestamps) present | **100% of records** | Entity constraint + detail page |

### DevOps success criteria
| ID | Criterion | Target | How measured |
|---|---|---|---|
| SC6 | Commit → built artefact without human steps | **100% automated** | Jenkins job triggered by SCM polling |
| SC7 | Pipeline duration (build → test → deploy) | **< 6 min** | Jenkins build time |
| SC8 | Failing Selenium test blocks deployment | **Always** | Deliberate defect run (Task 10) |
| SC9 | Re-running the Ansible playbook changes nothing | **changed=0** | Second run output (Task 14) |
| SC10 | Rollback to previous image | **< 2 min** | Timed rollback demo (Task 14) |
| SC11 | Critical user journeys covered by automated tests | **≥ 4 journeys** | Selenium suite (Task 9) |

---

## 9. Out of Scope (explicitly excluded from this MVP)

| Excluded | Reason |
|---|---|
| Machine-learning churn prediction | Risk score is an input, not a feature; C1 |
| Real email / SMS delivery of offers | No gateway; C6 |
| CRM / billing system integration | No external systems available; C1 |
| Self-service customer portal | Internal tool only |
| Multi-tenancy, multi-currency, i18n | C1 |
| Kubernetes / cloud orchestration | C2 — Docker + Ansible is the prescribed depth |
| SSO / LDAP authentication | C1; form login with roles is sufficient to prove RBAC |
| Mobile app | C8 |

---

## 10. Approved MVP Scope — frozen at 15 backlog items

The MVP is frozen at the following **15 items**. Items are traced to objectives and carried
forward unchanged into the Task 2 backlog and task board.

### Application scope (M1–M9)

| # | MVP item | Covers | Objective |
|---|---|---|---|
| M1 | Role-based login and logout (Agent / Manager / Admin) | Authentication + RBAC foundation | B1, B4 |
| M2 | **Create** a retention offer record | Create | B2 |
| M3 | **View** the offer list with pagination | View | B2 |
| M4 | **View** a single offer detail page with audit trail | View | B4 |
| M5 | **Update** / edit an offer while in Draft | Update | B2 |
| M6 | **Search and filter** offers (text + status + segment + risk) | Search | B2, P5 |
| M7 | Role-based **status workflow** with enforced transitions | Workflow | B1 |
| M8 | Manager approve / reject with decision comment | Workflow | B1, B4 |
| M9 | **Summary dashboard** — KPIs, status funnel, risk breakdown | Reporting | B3 |

### DevOps scope (M10–M15)

| # | MVP item | Covers | Objective |
|---|---|---|---|
| M10 | Git repository, branching policy, reviewed pull requests, release tag | SCM | T1 |
| M11 | Jenkins CI job: commit-triggered Maven build with archived artefact | CI | T2 |
| M12 | Declarative `Jenkinsfile` pipeline deploying the WAR to Tomcat | CD | T2 |
| M13 | Selenium regression suite integrated into the pipeline as a deployment gate | Continuous testing | T3 |
| M14 | Dockerfile, versioned image, local registry publication, auto-deployed container | Containerisation | T4 |
| M15 | Ansible provisioning playbook with idempotency, health check and rollback | Config management | T5, T6 |

**Definition of MVP done:** all 15 items complete, Selenium suite green in Jenkins, versioned
image in the registry, container deployed automatically after a commit, Ansible playbook
idempotent on re-run, and a successful rollback demonstrated.

---

## 11. Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Port conflicts between Jenkins, Tomcat, container and registry | High | Medium | Fixed port map documented in Task 3 and in the README |
| R2 | Selenium flakiness fails a good build | Medium | Medium | Explicit waits, headless Chrome, screenshot-on-failure |
| R3 | Jenkins cannot reach the Docker daemon | Medium | High | Jenkins runs as a native service as the same user that owns the Docker socket |
| R4 | Toolchain JDK mismatch (JDK 27 on PATH vs JDK 17 target) | High | Medium | `JAVA_HOME` pinned to JDK 17 in scripts and in the `Jenkinsfile` |
| R5 | Scope creep from "nice to have" UI ideas | Medium | High | Scope frozen at 15 items in this document |

---

## 12. Sign-off

| Role | Name | Decision | Date |
|---|---|---|---|
| Project sponsor (simulated) | Head of Retention | Scope approved | 2026-10-17 |
| Developer / DevOps engineer | Shreya Sathish | Scope frozen at 15 items | 2026-10-17 |

---

## Deliverable checklist for Task 1

- [x] Problem statement — §2
- [x] Stakeholder list — §4
- [x] Objectives — §6
- [x] Constraints — §7
- [x] Approved MVP scope (15 items) — §10
- [x] Target users, pain points, success criteria — §3, §5, §8
- [x] Evidence screenshot — `docs/screenshots/task-01-scope.png`
