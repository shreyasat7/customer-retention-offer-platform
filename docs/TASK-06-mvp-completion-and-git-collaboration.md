# Task 6 — MVP Completion and Git Collaboration

**Backlog items delivered:** M5 (update) · M6 (search) · M7 (workflow) · M8 (approve/reject) · M9 (dashboard)
**Second feature branch:** `feature/workflow-search-dashboard`
**Third branch:** `bugfix/17-sort-highest-risk-first` — created specifically to force a merge conflict
**Pull requests:** [#18](https://github.com/shreyasat7/customer-retention-offer-platform/pull/18), [#19](https://github.com/shreyasat7/customer-retention-offer-platform/pull/19), [#20](https://github.com/shreyasat7/customer-retention-offer-platform/pull/20)
**Release tag:** [`v1.0.0`](https://github.com/shreyasat7/customer-retention-offer-platform/releases/tag/v1.0.0)

---

## 1. Functional MVP — all nine application items

| Item | Capability | Where it lives |
|---|---|---|
| M1 | Role-based login and logout | `SecurityConfig`, `login.html` |
| M2 | Create a retention offer | `OfferController.create`, `offers/form.html` |
| M3 | View the paginated offer list | `OfferController.list`, `offers/list.html` |
| M4 | View offer detail with audit trail | `OfferController.detail`, `offers/detail.html` |
| **M5** | **Update an offer in Draft** | `OfferService.update` — re-checks `isEditable()` |
| **M6** | **Search and filter** | `RetentionOfferRepository.search` + the filter bar |
| **M7** | **Role-based status workflow** | `OfferWorkflow` |
| **M8** | **Manager approve / reject with comment** | `OfferWorkflow` + the detail page workflow panel |
| **M9** | **Summary dashboard** | `DashboardService`, `dashboard.html` |

### M5 — Update

Only `DRAFT` and `REJECTED` are editable. The **Edit** button is hidden elsewhere, but that
is a convenience, not the control: `OfferService.update` re-checks and throws
`OfferNotEditableException`, so a direct `POST /offers/{id}` cannot bypass the rule
(US-05 AC2). An edit copies only the commercial fields — the case reference, status and
every audit field are untouched, so an edit can never rewrite who approved what (NFR-04).

### M6 — Search and filter

One repository query where every criterion is optional:

```sql
WHERE (:q IS NULL OR LOWER(customerName)  LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(customerEmail) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(caseRef)       LIKE LOWER(CONCAT('%', :q, '%')))
  AND (:status  IS NULL OR status  = :status)
  AND (:segment IS NULL OR segment = :segment)
  AND (:minRisk IS NULL OR churnRisk >= :minRisk)
```

Blank controls are normalised to `null` in the service, so an empty field drops out of the
query rather than matching nothing. Active filters are shown as chips and are carried by
the pagination links, so moving to page 2 keeps the criteria (US-06 AC4).

### M7 / M8 — The workflow engine

`OfferWorkflow` is the **single authority** on how an offer moves. The whole state machine
is one table, and absence from it means refused:

```java
private static final Map<OfferStatus, Map<WorkflowAction, OfferStatus>> TRANSITIONS = Map.of(
        DRAFT,            Map.of(SUBMIT, PENDING_APPROVAL),
        PENDING_APPROVAL, Map.of(APPROVE, APPROVED, REJECT, REJECTED),
        REJECTED,         Map.of(REVISE, DRAFT),
        APPROVED,         Map.of(SEND, SENT),
        SENT,             Map.of(ACCEPT, ACCEPTED, DECLINE, DECLINED),
        ACCEPTED,         Map.of(),
        DECLINED,         Map.of());
```

Each action also declares the role it requires, so an agent cannot approve and a manager
cannot send. Every applied transition stamps the acting user and the time.

The controller does not decide anything — it hands the action to the workflow and reports
the outcome. A refused transition is a business outcome, not a crash: the user returns to
the offer with the reason and the record is unchanged.

### M9 — Dashboard

Total offers, awaiting approval, acceptance rate and monthly revenue retained; a
seven-state status funnel; a churn-risk donut; and the oldest offers still waiting on a
decision. Figures are derived from the offer table per request rather than kept in
incrementally updated counters — at demo scale that is simpler and far easier to prove
correct.

---

## 2. Unit Tests — proving success criterion SC1

```
$ mvn test
[INFO] Running com.crop.retention.service.OfferWorkflowTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.crop.retention.service.OfferTypeFormatTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The decisive test walks **every** status in the enum:

```java
@ParameterizedTest(name = "send is refused from {0}")
@EnumSource(value = OfferStatus.class,
        names = {"DRAFT", "PENDING_APPROVAL", "REJECTED", "SENT", "ACCEPTED", "DECLINED"})
void sendIsRefusedFromEveryStatusExceptApproved(OfferStatus status) {
    RetentionOffer o = offerIn(status);
    assertThatThrownBy(() -> workflow.apply(o, SEND, "agent", AGENT, null))
            .isInstanceOf(OfferWorkflow.IllegalTransitionException.class);
    assertThat(o.getStatus()).isEqualTo(status);   // record untouched
    assertThat(o.getSentAt()).isNull();
    assertThat(o.getSentBy()).isNull();
}
```

Because every transition goes through one choke point, **SC1 — no offer reaches a customer
without a manager's approval — is proven by a test rather than argued in a review.**

---

## 3. Second Feature Branch

```bash
git checkout develop
git checkout -b feature/workflow-search-dashboard
```

Five commits, each one logical change:

| Commit | Change |
|---|---|
| `144c5de` | `feat(workflow): add the enforced status workflow engine` |
| `08e518f` | `feat(offers): add edit, combined search and the workflow controls` |
| `f3c860a` | `feat(dashboard): add the retention KPI dashboard` |
| `8da669a` | `test(workflow): prove SC1 - sent is reachable only from approved` |
| `6ae2ff5` | `fix(ui): rename the workflow model attribute to avoid layout shadowing` |

### A defect found and fixed on this branch

The detail page rendered an **empty Workflow card** — no buttons and not even the
"nothing to do here" message.

The layout fragment is `shell(pageTitle, pageSub, active, actions, body)`, and the page
body is rendered *inside that fragment's scope*. So the fragment parameter named `actions`
shadowed the model attribute of the same name, and `${actions}` in the body resolved to
the header fragment instead of the list of available transitions. Renamed to
`workflowActions`, with a comment on the line saying why it must not be called `actions`.

---

## 4. Merge Conflict — created and resolved

### How the conflict was created

A third branch was raised from `develop` for [issue #17](https://github.com/shreyasat7/customer-retention-offer-platform/issues/17) — *"Offer list buries the highest-risk customers on later pages"* — and merged into `develop` through [PR #18](https://github.com/shreyasat7/customer-retention-offer-platform/pull/18) **while the feature branch was still in progress**. Both branches changed the same lines of `OfferController.list()`:

| Branch | Change to `list()` |
|---|---|
| `bugfix/17-sort-highest-risk-first` | Replaced the inline `Sort` with a named `HIGHEST_RISK_FIRST` constant |
| `feature/workflow-search-dashboard` | Added the `q` / `status` / `segment` / `minRisk` parameters and its own inline `Sort` |

### The conflict

```
$ git checkout feature/workflow-search-dashboard
$ git merge develop
Auto-merging src/main/java/com/crop/retention/web/OfferController.java
CONFLICT (content): Merge conflict in src/main/java/com/crop/retention/web/OfferController.java
Auto-merging src/main/resources/templates/offers/list.html
Automatic merge failed; fix conflicts and then commit the result.

$ git status
Unmerged paths:
  (use "git add <file>..." to mark resolution)
	both modified:   src/main/java/com/crop/retention/web/OfferController.java
```

```java
<<<<<<< HEAD
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) OfferStatus status,
                       @RequestParam(required = false) Segment segment,
                       @RequestParam(required = false) Integer minRisk,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {

        Page<RetentionOffer> results = offers.search(q, status, segment, minRisk,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                        Sort.by(Sort.Direction.DESC, "churnRisk").and(Sort.by(Sort.Direction.DESC, "updatedAt"))));

=======
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<RetentionOffer> results = offers.list(
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, HIGHEST_RISK_FIRST));
>>>>>>> develop
```

Note that `list.html` auto-merged — only the Java file needed a human decision.

### The resolution

Neither side was simply chosen. **Both were kept**: the search signature from the feature
branch and the named ordering constant from `develop`.

```java
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) OfferStatus status,
                       @RequestParam(required = false) Segment segment,
                       @RequestParam(required = false) Integer minRisk,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {

        // Conflict resolution (develop <- bugfix/17): this branch added the search and
        // filter parameters while develop replaced the inline Sort with the named
        // HIGHEST_RISK_FIRST constant. Both are kept - the search signature from here,
        // the named ordering from develop - because they are the same intent expressed
        // in two places, and the constant says why the ordering exists.
        Page<RetentionOffer> results = offers.search(q, status, segment, minRisk,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, HIGHEST_RISK_FIRST));
```

The resolution is commented on the line so the next reader knows the merge was a
**decision**, not an accident.

### Verification after resolution

```
$ mvn clean package     ->  BUILD SUCCESS
$ mvn test              ->  Tests run: 21, Failures: 0, Errors: 0
```

Full transcript: `docs/logs/task-06-merge-conflict.log`.

---

## 5. Branch Graph

```
$ git log --oneline --graph --decorate --all
*   1cd8bfd (HEAD -> main, tag: v1.0.0, origin/main) Merge pull request #20 from shreyasat7/develop
|\
| *   8e37cd7 (origin/develop, develop) Merge pull request #19 from .../feature/workflow-search-dashboard
| |\
| | *   bfeae97 merge: resolve offer-list ordering conflict with develop
| | |\
| | |/
| |/|
| * |   5f397b7 Merge pull request #18 from .../bugfix/17-sort-highest-risk-first
| |\ \
| | * | 2466b71 fix(offers): order the offer list by highest churn risk first
| |/ /
| | * 6ae2ff5 fix(ui): rename the workflow model attribute to avoid layout shadowing
| | * 8da669a test(workflow): prove SC1 - sent is reachable only from approved
| | * f3c860a feat(dashboard): add the retention KPI dashboard
| | * 08e518f feat(offers): add edit, combined search and the workflow controls
| | * 144c5de feat(workflow): add the enforced status workflow engine
| |/
| * f8efacb docs(planning): add task 5 feature-branch and pull-request record
| *   0993704 Merge pull request #16 from feature/create-view-offer
```

Every branch is visible, both merge commits are visible, and the conflict resolution sits
at `bfeae97` with its two parents.

---

## 6. Release Tag

`main` is protected, so the release went in as a pull request like everything else:

```bash
gh pr create --base main --head develop --title "release: v1.0.0 — MVP complete (M1–M9)"
gh pr merge --merge                                 # PR #20

git checkout main && git pull origin main
git tag -a v1.0.0 -m "v1.0.0 — MVP baseline"
git push origin v1.0.0
```

```
$ git describe --tags
v1.0.0
```

The annotated tag records what the baseline contains and the quality gate it passed. A
GitHub Release was published from it:
<https://github.com/shreyasat7/customer-retention-offer-platform/releases/tag/v1.0.0>

**Why tag now.** `v1.0.0` is the *source baseline the pipelines build*. Jenkins, Docker and
Ansible (items M11–M15) come after it, so the tag marks a known-good application before
any delivery automation is layered on.

---

## 7. Updated Backlog

### Status after Task 6

| ID | Item | Points | Status | Evidence |
|---|---|---|---|---|
| US-01 | Role-based login and logout | 5 | ✅ **Done** | Task 3 / Task 4 commits |
| US-02 | Create a retention offer | 5 | ✅ **Done** | PR #16 |
| US-03 | View the offer list | 3 | ✅ **Done** | PR #16 |
| US-04 | View offer detail with audit trail | 3 | ✅ **Done** | PR #16 |
| US-05 | Update an offer in Draft | 3 | ✅ **Done** | PR #19 |
| US-06 | Search and filter offers | 5 | ✅ **Done** | PR #19 |
| US-07 | Role-based status workflow | 8 | ✅ **Done** | PR #19 + 17 unit tests |
| US-08 | Approve / reject with comment | 3 | ✅ **Done** | PR #19 |
| US-09 | Summary dashboard | 8 | ✅ **Done** | PR #19 |
| US-10 | Version control, branch policy, PRs, tag | 3 | ✅ **Done** | PRs #16, #18, #19, #20 + `v1.0.0` |
| US-11 | Continuous integration build | 5 | ⏳ In progress | Task 7 |
| US-12 | Pipeline as code + deploy | 5 | 📋 Ready | Task 8 |
| US-13 | Selenium deployment gate | 8 | 📋 Ready | Tasks 9–10 |
| US-14 | Docker image + registry + auto-deploy | 8 | 📋 Ready | Tasks 11–12 |
| US-15 | Ansible provisioning + rollback | 8 | 📋 Ready | Tasks 13–14 |

**Delivered: 46 of 80 story points (58%).** Every application item is complete; the
remaining 34 points are the delivery pipeline.

### Items added to the backlog during Sprint 2

| ID | Item | Source | Status |
|---|---|---|---|
| #17 | Offer list buries the highest-risk customers | Found while reviewing seeded data | ✅ Done — PR #18 |
| — | `1 months` pluralisation defect | PR #16 review comment 1 | ✅ Done — `e5991e3` + regression test |
| — | Inline row click handler is keyboard-inaccessible | PR #16 review comment 2 | ✅ Done — `e5991e3` |
| — | `GET /offers/new` returned 500 | Found reading the PR #16 diff | ✅ Done — `a5e2095` |
| — | Empty Workflow card from layout shadowing | Found verifying PR #19 | ✅ Done — `6ae2ff5` |

### Carried forward

Nothing. No application scope slipped out of the MVP.

---

## 8. Collaboration Summary

| Metric | Count |
|---|---|
| Branches used | 5 — `main`, `develop`, 2 × `feature/*`, 1 × `bugfix/*` |
| Pull requests | 4 (#16, #18, #19, #20) |
| Inline review comments | 3, plus 2 summary reviews and 2 follow-up comments |
| Defects found by review | 2 (HTTP 500 on the create form, offer-unit pluralisation) |
| Merge conflicts created and resolved | 1 |
| Merge commits preserving topology | 4 |
| Release tags | 1 (`v1.0.0`) |
| Unit tests at the tag | 21, all passing |

---

## Deliverable checklist for Task 6

- [x] Functional MVP — create, view, update, search; role-based status workflow; summary dashboard — §1
- [x] Second feature branch (`feature/workflow-search-dashboard`) — §3
- [x] Merge conflict **created** deliberately — §4
- [x] Merge conflict **resolved**, with the reasoning recorded in the code — §4
- [x] Tagged version `v1.0.0` on `main`, pushed, with a GitHub Release — §6
- [x] Release-ready source baseline — `mvn clean package` green, 21 tests passing — §2
- [x] Updated backlog — §7
- [x] Evidence screenshots — `task-06-dashboard.png`, `task-06-search-filter.png`, `task-06-workflow-manager.png`, `task-06-workflow-agent-blocked.png`, `task-06-workflow-approved.png`, `task-06-merge-conflict.png`, `task-06-git-graph-and-tag.png`, `task-06-github-release.png`, `task-06-pull-requests.png`
