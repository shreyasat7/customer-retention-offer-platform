# Task 4 — Git and GitHub Repository Initialization

**Repository URL:** <https://github.com/shreyasat7/customer-retention-offer-platform>
**Visibility:** Public
**Default branch:** `main` (protected) · **Integration branch:** `develop`

---

## 1. Repository Creation

```bash
# local repository, main as the initial branch
git init -b main

# first six commits (see §4)
git add … && git commit -m "…"

# create the GitHub remote and push in one step
gh repo create customer-retention-offer-platform \
   --public --source=. --remote=origin \
   --description "Role-based platform for raising, approving and tracking customer
                  retention offers — delivered end to end by an automated DevOps pipeline" \
   --push

# integration branch
git checkout -b develop
git push -u origin develop
```

Result:

```
$ git branch -a
  develop
* main
  remotes/origin/develop
  remotes/origin/main

$ git remote -v
origin  https://github.com/shreyasat7/customer-retention-offer-platform.git (fetch)
origin  https://github.com/shreyasat7/customer-retention-offer-platform.git (push)
```

---

## 2. README

`README.md` covers, in this order:

| Section | Contents |
|---|---|
| Why this exists | The retention problem in two paragraphs |
| What it does | Create / view / update / search, role-based workflow, dashboard |
| Roles | `ROLE_AGENT`, `ROLE_MANAGER`, `ROLE_ADMIN` and what each may do |
| Quick start | Clone → `. scripts/env.sh` → `mvn clean package` → `./scripts/run-local.sh` |
| Demo accounts | The three local accounts, with an explicit note that the instance is synthetic and localhost-only |
| Technology | Table of every tool with its role in the project |
| Port map | All eight ports used across the toolchain |
| Repository layout | Annotated directory tree |
| Branching policy | The four branch types and the six rules |
| Documentation | Link to all 14 task documents |
| Health check | The `curl` command and why the endpoint is unauthenticated |

---

## 3. `.gitignore`

Grouped by what it protects against:

```gitignore
# build
target/
*.war
*.jar

# application runtime state
data/          # the H2 file database — must never be committed
*.mv.db
logs/
*.log

# IDE
.idea/  *.iml  .vscode/  .settings/  .classpath  .project

# OS
.DS_Store

# local-only artefacts
.local/            # generated local credentials, never committed
.evidence-venv/    # Python venv used by the screenshot helpers
```

The `data/` exclusion matters: the H2 file database holds seeded demo records and would
otherwise create a merge conflict on every pull.

---

## 4. Folder Structure Committed

```
.
├── README.md                     project front door
├── CONTRIBUTING.md               branch naming + commit convention
├── .gitignore
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── user-story.md         story + acceptance criteria + DoD checklist
│   │   ├── bug-report.md         expected/actual, repro, environment, severity
│   │   └── devops-task.md        goal, scope, evidence required
│   └── pull_request_template.md  change, issue link, AC covered, test evidence, reviewer checklist
├── pom.xml
├── .mvn/maven.config
├── scripts/                      env.sh, run-local.sh, stop-local.sh, evidence/
├── src/main/java/com/crop/retention/{config,model,repository,web}
├── src/main/resources/{application.properties,templates,static/css}
└── docs/                         task documents, screenshots, logs, diagrams
```

---

## 5. Initial Commits

Six commits, each one logical change, following the `type(scope): subject` convention
from `CONTRIBUTING.md`:

```
$ git log --oneline --graph --decorate
* e7e15d7 feat(ui): add application shell, design system and skeleton screens
* df8b363 feat(security): add form login for agent, manager and administrator roles
* a418955 feat(domain): add retention offer aggregate with enums and search repository
* f8d6b6f chore(build): add Maven build with WAR packaging and JDK 17 pinning
* bf2ff6a docs(planning): add task 1-3 deliverables with diagrams and evidence
* fb5c48a chore(repo): initialise repository with README, branch policy and issue templates
```

### Why these six, in this order

| # | Commit | Rationale |
|---|---|---|
| 1 | `chore(repo)` | The rules of the repository land before any code, so every later commit is already governed by them |
| 2 | `docs(planning)` | Tasks 1–3 are design work and belong in history before implementation |
| 3 | `chore(build)` | The build must exist before there is anything to build |
| 4 | `feat(domain)` | The aggregate and its persistence — the foundation everything else depends on |
| 5 | `feat(security)` | Backlog item M1; authentication gates every other screen |
| 6 | `feat(ui)` | The shell and design system the remaining screens are built into |

Commit bodies explain **why**, not what. For example, commit 3 records *why* `JAVA_HOME` is
pinned (the host's `mvn` defaults to JDK 27 while the project targets 17 — project risk R4),
which a diff alone would never show.

---

## 6. Issues

All 15 backlog items from Task 2 were raised as GitHub issues, each carrying its user
story, acceptance criteria as a checklist, MVP item, story points, sprint and the
Definition-of-Done checklist.

| # | Issue | Labels |
|---|---|---|
| 1 | [US-01] Role-based login and logout | `user-story` `sprint-1` `must-have` |
| 2 | [US-02] Create a retention offer | `user-story` `sprint-1` `must-have` |
| 3 | [US-03] View the offer list | `user-story` `sprint-1` `must-have` |
| 4 | [US-04] View offer detail with audit trail | `user-story` `sprint-1` `must-have` |
| 5 | [US-05] Update an offer in Draft | `user-story` `sprint-2` `must-have` |
| 6 | [US-06] Search and filter offers | `user-story` `sprint-2` `must-have` |
| 7 | [US-07] Role-based status workflow | `user-story` `sprint-2` `must-have` |
| 8 | [US-08] Manager approve / reject with comment | `user-story` `sprint-2` `must-have` |
| 9 | [US-09] Summary dashboard | `user-story` `sprint-2` `must-have` |
| 10 | [US-10] Version control with branch policy and reviewed pull requests | `devops` `sprint-1` `must-have` |
| 11 | [US-11] Continuous integration build | `devops` `sprint-1` `must-have` |
| 12 | [US-12] Pipeline as code with server deployment | `devops` `sprint-2` `must-have` |
| 13 | [US-13] Selenium regression suite as a deployment gate | `devops` `sprint-2` `must-have` |
| 14 | [US-14] Containerised, registry-published, auto-deployed image | `devops` `sprint-2` `must-have` |
| 15 | [US-15] Idempotent provisioning with health check and rollback | `devops` `sprint-2` `must-have` |

### Labels created

| Label | Colour | Meaning |
|---|---|---|
| `user-story` | green | A backlog item delivering user-facing value |
| `devops` | purple | Pipeline, container or infrastructure work |
| `sprint-1` | light blue | Walking skeleton and build pipeline |
| `sprint-2` | dark blue | Complete MVP and automate delivery |
| `must-have` | red | Must-have for the frozen MVP scope |

---

## 7. Branch Policy

### Branch types

| Branch | Purpose | How code gets in | Lifetime |
|---|---|---|---|
| `main` | Release-ready baseline only | Pull request from `develop`, tagged on merge | Permanent |
| `develop` | Integration of completed stories | Pull request from `feature/*` only | Permanent |
| `feature/<short-kebab-name>` | Exactly one user story | Branched from `develop`, merged back by PR | Short-lived, deleted after merge |
| `bugfix/<issue>-<short-kebab-name>` | A defect fix | Same rules as `feature/*` | Short-lived |

### Naming rules

- Lower-case kebab-case only: `feature/create-view-offer`, not `Feature/CreateViewOffer`.
- One backlog item per branch; if a branch grows a second concern, split it.
- Bug branches lead with the issue number: `bugfix/12-send-without-approval`.

### Enforced rules on `main`

Branch protection was applied through the GitHub API:

```bash
gh api -X PUT repos/shreyasat7/customer-retention-offer-platform/branches/main/protection \
  --input protection.json
```

| Rule | State |
|---|---|
| Require a pull request before merging | **enabled** |
| Dismiss stale reviews on new commits | **enabled** |
| Allow force pushes | **disabled** |
| Allow branch deletion | **disabled** |

Verified:

```
$ gh api repos/shreyasat7/customer-retention-offer-platform/branches/main/protection
required_pull_request_reviews: enabled
allow_force_pushes: False
allow_deletions: False
```

> `enforce_admins` is left off deliberately: this is a single-developer project and the
> owner must still be able to administer the repository. The rule that matters —
> *no direct push of code to `main`* — is in force.

### Commit message convention

```
type(scope): subject

[body — why, not what]
```

`type` ∈ `feat`, `fix`, `test`, `docs`, `chore`, `refactor`.
`scope` ∈ `domain`, `security`, `ui`, `workflow`, `search`, `dashboard`, `build`, `ci`,
`docker`, `ansible`, `repo`.
Subject is imperative, lower case, no trailing full stop, ≤ 72 characters.

---

## 8. Verification

| Deliverable | Evidence |
|---|---|
| Repository URL | <https://github.com/shreyasat7/customer-retention-offer-platform> |
| README | `docs/screenshots/task-04-github-repo.png` — rendered on the repository home page |
| `.gitignore`, folder structure | Committed in commit 1 and 6 |
| Issue templates | `.github/ISSUE_TEMPLATE/` — three templates plus a PR template |
| Initial commits | `docs/screenshots/task-04-commits.png`, `docs/logs/task-04-git-init.log` |
| Issues | `docs/screenshots/task-04-github-issues.png` — 15 open issues with labels |
| Branch policy | §7, `CONTRIBUTING.md`, README, and live branch protection on `main` |

---

## Deliverable checklist for Task 4

- [x] GitHub repository created and pushed — §1
- [x] README — §2
- [x] `.gitignore` — §3
- [x] Folder structure — §4
- [x] Issue templates (3) and PR template — §4
- [x] Branch naming rules — §7
- [x] Initial application skeleton committed with meaningful messages (6 commits) — §5
- [x] Issues raised (15) — §6
- [x] Branch policy documented **and enforced** — §7
- [x] Evidence screenshots — `task-04-github-repo.png`, `task-04-github-issues.png`, `task-04-commits.png`, `task-04-git-log.png`
