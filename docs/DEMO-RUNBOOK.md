# Demonstration Runbook

A ~20 minute live walkthrough of all 14 tasks. Everything runs on this machine.

---

## 0. Before the demo (5 minutes earlier)

```bash
cd ~/Desktop/devops_project_
./scripts/demo-up.sh        # starts Docker, Jenkins, Tomcat, registry, container, nginx
./scripts/demo-status.sh    # everything should be green
```

`demo-up.sh` is safe to re-run. If anything shows **DOWN**, run it again.

### Open these tabs in advance

| # | Tab | URL |
|---|---|---|
| 1 | Application | <http://localhost:8081> |
| 2 | Jenkins | <http://localhost:8080> |
| 3 | GitHub repo | <https://github.com/shreyasat7/customer-retention-offer-platform> |
| 4 | Registry UI | <http://localhost:5002> |
| 5 | Dozzle (Docker) | <http://localhost:8092> |
| 6 | Tomcat deployment | <http://localhost:9090/crop> |

### Logins

| Where | Username | Password |
|---|---|---|
| Application — agent | `agent` | `agent123` |
| Application — manager | `manager` | `manager123` |
| Application — admin | `admin` | `admin123` |
| Jenkins | `admin` | `admin123` |

> All local, synthetic data only, bound to `127.0.0.1`.

---

## 1. The problem and the plan — Tasks 1–2 · 2 min

Open `docs/screenshots/task-01-scope.png` and `task-02-board-and-workflow.png`.

**Say:** retention offers are run from spreadsheets with no enforced approval, so discount
margin leaks and nobody can prove which offers worked. Scope was frozen at **15 backlog
items** with **11 measurable success criteria**, planned over 2 sprints on a Kanban board
with a WIP limit of 2.

Point at the **DevOps lifecycle diagram** — Plan → Code → Build → Test → Release → Deploy
→ Operate → Monitor — and note the **quality gate** between Test and Release. Everything
after this is that diagram running for real.

---

## 2. The application — Task 3, 5, 6 · 4 min

**Tab 1 — <http://localhost:8081>**

1. Sign in as **manager** → the **dashboard** is the landing page.
   Point at acceptance rate, revenue retained, the seven-state funnel and the churn-risk
   donut.
2. **All offers** → the list. Point at pagination, status pills, risk bars.
3. Type `pill` in Search, set **Min. churn risk** `70`, press **Search** →
   active-filter chips appear, the result set narrows.
4. Open **CRO-0003** → show the **audit trail** on the right: created by, submitted by,
   decided by, each with a timestamp.
5. On a *Pending approval* offer as **manager**, show **Approve** and **Reject** with a
   mandatory comment box.
6. **Sign out, sign in as `agent`**, open the same offer → *"Nothing for your role to do
   here"*, and **New offer** is now in the sidebar.

**The line to land:** the approval step is part of the workflow, not an email.
`SENT` is reachable only from `APPROVED` — and that is proven by a test, next.

---

## 3. Git collaboration — Tasks 4–6 · 2 min

**Tab 3 — GitHub**

- **Issues** → 15 backlog items with acceptance criteria.
- **Pull requests** → 4 merged. Open **#16** → *Files changed* → the three inline review
  comments, and the comment where reading the diff caught a **500 error** on the create
  form that the happy-path check had missed.
- **Releases** → `v1.0.0`.

**Terminal:**

```bash
git log --oneline --graph --decorate --all | head -20
```

Point at the two feature branches, the bugfix branch, the **merge-conflict resolution
commit** and the `v1.0.0` tag.

---

## 4. Jenkins — Tasks 7–8 · 3 min

**Tab 2 — <http://localhost:8080>**

- **crop-ci-build** → *Git Polling Log* → `Changes found`. A commit produced a build with
  **no button pressed**. Show the archived `crop.war` and the test-result trend.
- **crop-pipeline** → the **Stage View**. Eleven stages, defined by the `Jenkinsfile` in
  the repository — the delivery process is reviewed like code.
- **Build with Parameters** → `TOMCAT_HOME`, `DEPLOY_PORT`, `APP_CONTEXT`,
  `DOCKER_REGISTRY`, `CONTAINER_PORT` — environment settings, not hard-coded values.

**Tab 6 — <http://localhost:9090/crop>** — the same application, deployed to Tomcat by the
pipeline.

---

## 5. The quality gate — Tasks 9–10 · 3 min  ← the strongest part

**Tab 2 — crop-pipeline → Build History.**

Point at **#6 (red)** and **#7 (green)**.

Open **#6** → Stage View:

| Stage | #6 |
|---|---|
| Selenium regression gate | ❌ **failed** |
| Deploy to Tomcat | **skipped** |
| Container health check | **skipped** |

**Say:** a UI regression was introduced on purpose — a search filter was silently disabled.
The unit tests passed; they could not see it. The Selenium suite caught it, the build went
**FAILURE**, and **the deploy never ran**. The server kept serving the last good build.

- **#6 → Artifacts** → the automatic **failure screenshot** of the page as it looked.
- **#6 → Test Result** → 1 failure of 33.
- Sidebar → **Selenium regression report**.

**The honest bit worth saying:** the first attempt at the defect *passed*. The assertion
only checked that every returned row satisfied the filter — and the seeded data satisfied
it anyway. That exposed a weak test, not a strong pipeline. The assertion was strengthened
to require the result count to actually drop, and *then* the gate caught it.

Optionally, run it live:

```bash
. scripts/env.sh && mvn clean verify     # 21 unit + 33 Selenium tests, ~90 s
```

---

## 6. Docker — Tasks 11–12 · 3 min

**Terminal:**

```bash
docker ps
docker inspect crop-app --format 'build={{index .Config.Labels "crop.build"}} commit={{index .Config.Labels "crop.commit"}}'
git rev-parse --short HEAD
```

**The container's commit label equals the repository HEAD.**

**Tab 5 — <http://localhost:8092>** (Dozzle) — live containers, CPU, memory, streaming
logs. Click `crop-app` → logs.

**Tab 4 — <http://localhost:5002>** (Registry UI) → `crop` → the tag list, one per Jenkins
build, with content digests.

**Say:** the pipeline builds the image from the WAR the Selenium gate just tested, tags it
with the build number, pushes it, then **pulls it back from the registry** and replaces
the container — so the deploy exercises the artefact that was actually published.

You can also open **Docker Desktop** to show the same images and containers in Docker's
own GUI.

---

## 7. Ansible — Tasks 13–14 · 3 min

**Terminal, in `ansible/`:**

```bash
cd ansible
ansible-playbook site.yml
```

→ **`changed=0`**. *"Running it again changes nothing. That is idempotency."*

Then the rollback:

```bash
curl -s http://localhost:5001/v2/crop/tags/list       # the published builds
ansible-playbook rollback.yml -e rollback_to=11
```

→ *"rolled back from crop:13 to crop:11, health UP, data volume untouched"* — in about
**11 seconds**.

Refresh **Tab 1** → the sidebar version label is gone (the older UI), but **every number
on the dashboard is identical**: 24 offers, 77.8% acceptance, ₹35,108 retained.

```bash
docker logs crop-app | grep -i seed
# Database already contains 24 offers - skipping seed
```

**Say:** the container is disposable, the data is not. Roll forward the same way:

```bash
ansible-playbook rollback.yml -e rollback_to=13
```

---

## 8. The whole loop, live — optional finish · 2 min

If time allows, the single most convincing thing:

```bash
# make any small visible change, then:
git add -A && git commit -m "demo: live change" && git push origin develop
```

Watch Jenkins pick it up within two minutes, run eleven stages, publish a new image tag,
and replace the running container. Refresh the application — the change is live.

**One `git push`, no further human action.**

---

## Quick reference

| Command | Does |
|---|---|
| `./scripts/demo-up.sh` | Start everything |
| `./scripts/demo-status.sh` | One-screen status of every service |
| `./scripts/demo-down.sh` | Stop everything (nothing is deleted) |
| `. scripts/env.sh && mvn clean verify` | Run all tests locally |
| `cd ansible && ansible-playbook site.yml` | Re-provision (should be `changed=0`) |
| `cd ansible && ansible-playbook rollback.yml -e rollback_to=N` | Roll back or forward |

### If something is down mid-demo

| Symptom | Fix |
|---|---|
| Application not responding | `cd ansible && ansible-playbook site.yml` |
| Jenkins down | `brew services restart jenkins-lts`, wait ~30 s |
| Tomcat down | `/opt/homebrew/opt/tomcat/libexec/bin/startup.sh` |
| Docker down | Open Docker Desktop, wait, then `./scripts/demo-up.sh` |
| Anything else | `./scripts/demo-up.sh` — it is safe to re-run |

### Port map

| Port | Service |
|---|---|
| 8081 | Application via nginx reverse proxy |
| 8090 | Application container |
| 9090 | Application on Tomcat (`/crop`) |
| 8080 | Jenkins |
| 5001 / 5002 | Docker registry / its web UI |
| 8092 | Dozzle (Docker web interface) |
| 8070 / 8095 | Local development / Selenium test instance |
