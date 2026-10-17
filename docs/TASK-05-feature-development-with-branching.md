# Task 5 — Feature Development with Branching

**Feature:** the first core workflow — raise a retention offer, browse the pipeline, open a single offer
**Backlog items:** M2 (create) · M3 (view list) · M4 (view detail with audit trail)
**Issues closed:** [#2](https://github.com/shreyasat7/customer-retention-offer-platform/issues/2), [#3](https://github.com/shreyasat7/customer-retention-offer-platform/issues/3), [#4](https://github.com/shreyasat7/customer-retention-offer-platform/issues/4)
**Branch:** `feature/create-view-offer` → `develop`
**Pull request:** [#16](https://github.com/shreyasat7/customer-retention-offer-platform/pull/16) — merged

---

## 1. The Feature

| Endpoint | Role | What it does |
|---|---|---|
| `GET /offers` | authenticated | Paginated list, 10 per page, newest activity first |
| `GET /offers/new` | `ROLE_AGENT` | Create form |
| `POST /offers` | `ROLE_AGENT` | Validated create → `DRAFT` with a generated `CRO-####` reference |
| `GET /offers/{id}` | authenticated | Detail page with the full audit-trail timeline |

### What was deliberately left out

Status transitions (submit / approve / reject / send / outcome) are **not** in this feature.
They belong to `OfferWorkflow` in backlog item M7, so that there is exactly one authority
on legal movement between states. Rather than render buttons that do nothing, the detail
page says where those actions are coming from.

### Code added

```
src/main/java/com/crop/retention/
├── service/OfferService.java            application services; the only thing controllers call
├── web/OfferController.java             list, detail, new form, create
├── web/GlobalExceptionHandler.java      OfferNotFoundException -> styled 404 page
└── config/DataSeeder.java               24 synthetic demonstration offers
src/main/resources/templates/offers/
├── list.html                            table, status pills, risk bars, pagination
├── form.html                            create form with field-level validation
└── detail.html                          KPI strip, record, audit-trail timeline
```

---

## 2. Git Commands Used

### Branch and work

```bash
git checkout develop
git checkout -b feature/create-view-offer        # branch from develop, never from main

# ... implement ...

git add src/main/java/com/crop/retention/service/OfferService.java \
        src/main/java/com/crop/retention/web/GlobalExceptionHandler.java
git commit -m "feat(offers): add offer service with generated case references"

git add src/main/java/com/crop/retention/web/OfferController.java \
        src/main/resources/templates/offers/
git commit -m "feat(offers): add create form, paginated list and detail screens"

git add src/main/java/com/crop/retention/config/DataSeeder.java
git commit -m "chore(data): seed 24 synthetic demonstration offers on first start"

git push -u origin feature/create-view-offer
```

### Inspecting history

```bash
git log --oneline --graph --decorate --all
git log --stat -1
git diff develop..feature/create-view-offer --stat
git status --short
```

### Keeping up to date

```bash
git checkout develop
git pull origin develop          # fetch + merge the integration branch
git checkout develop && git pull # again after the merge, to sync the local branch
```

### Result

```
$ git log --oneline --graph --decorate -9
*   0993704 (HEAD -> develop, origin/develop) Merge pull request #16 from feature/create-view-offer
|\
| * a5e2095 fix(ui): render the validation summary inside the form's object scope
| * e5991e3 fix(ui): pluralise offer units and remove the inline row click handler
| * 88ba7a9 docs(planning): add task 5 screenshots
| * 46810d0 chore(data): seed 24 synthetic demonstration offers on first start
| * 1605a19 feat(offers): add create form, paginated list and detail screens
| * 83c9905 feat(offers): add offer service with generated case references
|/
* 7b5e7c1 docs(planning): record enforced branch protection on main
* 3e26217 (origin/main, main) docs(planning): add task 4 git and github initialisation record
```

A **merge commit** was used rather than a squash so the branch topology stays visible in
the graph — the whole point of the exercise is to show the branch and its merge.

---

## 3. Pull Request

**[PR #16 — feat(offers): create, list and view retention offers (M2–M4)](https://github.com/shreyasat7/customer-retention-offer-platform/pull/16)**
`feature/create-view-offer` → `develop` · 6 commits · 11 files changed · +752 −17

The description states what changed, the design decisions a reviewer needs (why
transitions were left out, why authorisation is on the controller and not only on the
button, how case references are allocated), the issues it closes, the acceptance criteria
covered, and how it was tested.

---

## 4. Review

Three review comments were raised as inline comments on the diff, plus a summary review.

| # | File / line | Comment | Severity |
|---|---|---|---|
| 1 | `templates/offers/list.html:71` | **Pluralisation bug.** `OfferType.getUnit()` returns a fixed `" months"`, so a one-month offer renders as `1 months` — visible in `CRO-0018` and `CRO-0024`. The same expression is duplicated in `detail.html` twice. Move formatting onto the enum as `format(BigDecimal)` and call it from all three sites. | Must fix |
| 2 | `templates/offers/list.html:57` | **Accessibility / inline script.** The whole `<tr>` is clickable via `th:onclick`, which cannot be reached by keyboard and is the only inline JavaScript in the codebase. Drop it — the case-reference link already navigates. | Must fix |
| 3 | `service/OfferService.java:66` | **Concurrency note.** `AtomicInteger` + `existsByCaseRef` is safe in one JVM, but two instances on the same H2 file would each hold their own counter. Not a problem for this deployment; record the constraint in the Javadoc. | Note |

### A real defect found during review

Reading the **Files changed** tab surfaced something the happy-path check had missed: the
committed screenshot `task-05-new-offer-form.png` was a **500 error page**, not the form.

Root cause — Thymeleaf's `#fields` resolves against the enclosing `th:object`, and the
validation summary had been placed *above* the `<form>`:

```
Could not bind form errors using expression "all". Please check this expression is being
executed inside the adequate context (e.g. a <form> with a th:object attribute)
(template: "offers/form" - line 14, col 33)
```

`GET /offers/new` returned 500 for every user — the create journey was entirely broken.

**This is the argument for the pull-request step.** The defect was in a committed artefact
rather than in the code diff, and only reading the changed files caught it.

---

## 5. Review Responses

Two commits answered the review:

| Commit | Addresses |
|---|---|
| `e5991e3` `fix(ui): pluralise offer units and remove the inline row click handler` | Comments 1, 2 and 3 |
| `a5e2095` `fix(ui): render the validation summary inside the form's object scope` | The 500 defect, plus the duplicate account-ID validation message |

### Comment 1 — pluralisation

Formatting moved from three hand-built template expressions onto the enum:

```java
public String format(BigDecimal value) {
    ...
    return switch (this) {
        case DISCOUNT_PERCENT -> number + "%";
        case FREE_MONTHS      -> number + (singular ? " month"  : " months");
        case PLAN_UPGRADE     -> number + (singular ? " tier"   : " tiers");
        case LOYALTY_CREDIT   -> "₹" + number;
    };
}
```

`CRO-0018` now reads **1 month**, `CRO-0024` **2 months**, `CRO-0023` **₹15,000**. The
`getUnit()` field that caused the bug was deleted rather than left unused.

### Comment 2 — row click handler

`th:onclick` removed. Tab order is now case-reference link → next row, and no inline
JavaScript remains anywhere in the templates — which is also why the Selenium suite in
Task 9 is straightforward.

### Comment 3 — concurrency

Recorded in the `nextCaseRef()` Javadoc, including why a database sequence is *not* being
added for the MVP.

### The 500 defect

```html
<form th:action="@{/offers}" th:object="${offer}" method="post" novalidate>

  <!-- #fields resolves against the enclosing th:object, so the validation summary
       must live inside the form, not above it. -->
  <div class="alert alert--bad" th:if="${#fields.hasAnyErrors()}">
```

Verified after the fix:

```
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8070/offers/new
200
```

---

## 6. Merge Evidence

```bash
gh pr merge 16 --merge
```

```
Merged pull request #16 (feat(offers): create, list and view retention offers (M2-M4))
```

| Check | Result |
|---|---|
| Target branch | `develop` |
| Merge strategy | Merge commit — branch topology preserved |
| Merge commit | `0993704` |
| Conflicts | None (first feature branch off `develop`) |
| Issues closed automatically | #2, #3, #4 |
| Build after merge | `mvn clean package` → **BUILD SUCCESS** |

---

## 7. Verification

| Acceptance criterion | Evidence |
|---|---|
| US-02 AC1 — form captures all nine fields | `task-05-new-offer-form.png` |
| US-02 AC2 — create yields `DRAFT` + `CRO-####` + `createdBy` | `task-05-offer-detail.png` |
| US-02 AC3 — invalid input redisplays with field errors, persists nothing | `task-05-form-validation.png` |
| US-02 AC4 — manager gets 403 on `/offers/new` | `@PreAuthorize("hasRole('AGENT')")` on the controller |
| US-03 AC1 — list columns | `task-05-offer-list.png` |
| US-03 AC2 — pagination, 10 per page, 24 records over 3 pages | `task-05-offer-list.png` footer |
| US-03 AC3 — colour-coded status pills | `task-05-offer-list.png` |
| US-04 AC1 — detail shows every field plus audit fields | `task-05-offer-detail.png` |
| US-04 AC3 — unknown id → styled 404, not a stack trace | `GlobalExceptionHandler` |

---

## Deliverable checklist for Task 5

- [x] Working feature 1 — create, list and view offers — §1
- [x] Feature branch `feature/create-view-offer` — §2
- [x] `add`, `commit`, `push`, `pull` and `log` all used and shown — §2
- [x] Pull request raised — §3 — [PR #16](https://github.com/shreyasat7/customer-retention-offer-platform/pull/16)
- [x] Review comments (3 inline + 1 summary + a defect found) — §4
- [x] Review responses in follow-up commits — §5
- [x] Merged into the development branch — §6
- [x] Evidence screenshots — `task-05-offer-list.png`, `task-05-new-offer-form.png`, `task-05-form-validation.png`, `task-05-offer-detail.png`, `task-05-pull-request.png`, `task-05-pr-review-comments.png`, `task-05-pr-merged.png`, `task-05-git-workflow.png`
