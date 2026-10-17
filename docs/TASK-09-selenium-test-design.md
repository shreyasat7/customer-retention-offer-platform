# Task 9 — Selenium Test Design and Local Execution

**Suite:** 5 journeys · **32 Selenium tests** · headless Chrome 155 · Selenium 4.27.0
**Command:** `mvn clean verify` · **Result:** 21 unit + 32 Selenium tests, 0 failures
**Backlog item:** M13 · **Issue:** [#13](https://github.com/shreyasat7/customer-retention-offer-platform/issues/13)

---

## 1. Test Plan

### 1.1 How the journeys were chosen

Not "one test per page". Each journey is a path a real user takes where **a failure costs
the business something specific**, traced back to the pain points and objectives from
Task 1.

| # | Journey | Why it is critical | Traces to |
|---|---|---|---|
| **J1** | Sign in, role-based access, sign out | The gate on everything else. If authentication or role-driven navigation breaks, nothing else is reachable *or safe* | US-01, P1 |
| **J2** | Create a retention offer | The entry point of the product. No offer → nothing to approve, send or report | US-02, US-04, P2 |
| **J3** | Search and filter the pipeline | Duplicate offers reaching one customer is a named pain point. Broken search sends agents back to guessing | US-06, US-03, P5 |
| **J4** | Submit → approve → send → outcome | **The business rule the platform exists to enforce.** No discount reaches a customer without a recorded approval | US-07, US-08, B1, **SC1** |
| **J5** | Summary dashboard | The only thing management sees. Wrong numbers = no reporting value | US-09, B3, SC4 |

### 1.2 Coverage

| Journey | Tests | What it asserts |
|---|---|---|
| J1 | 6 | Redirect when unauthenticated · generic failure message · agent sees agent controls · manager does not · **manager gets 403 on `/offers/new` even by direct URL** · sign-out really ends the session |
| J2 | 5 | Empty form → ≥6 field errors and **nothing persisted** · invalid email caught by bean validation · risk outside 0–100 refused · valid offer → `DRAFT` + `CRO-####` + formatted value + risk band · audit trail present and record findable |
| J3 | 8 | Pagination 10/page · partial, case-insensitive name match · email match · case-reference match · **filters combine (status AND risk)** · empty state when nothing matches · Reset clears everything · **filters survive pagination** |
| J4 | 7 | Agent submits · agent cannot send before approval · **a forged POST to `/send` is refused and the record is untouched** · manager approves (and cannot send) · agent sends · agent records the outcome, offer becomes terminal · **rejection without a reason is refused**, then revise returns it to Draft |
| J5 | 6 | Dashboard is the landing page · four KPI tiles populated · **funnel accounts for every offer** · **risk bands account for every offer** · needs-attention ordered oldest first · renders in under 2 s (SC4) |

### 1.3 The assertion that matters most

```java
@Test
@DisplayName("SC1 - sending an unapproved offer is refused even when the request is forged")
void forgedSendIsRefused() {
    signIn("agent", "agent123");
    driver.get(offerUrl);
    assertThat(currentStatus()).isEqualTo("Pending approval");

    // the button is not rendered, so post the transition directly, the way a user
    // bypassing the UI would
    forgePost("/offers/" + id + "/send");

    assertThat(pageText()).contains("Action refused");
    assertThat(currentStatus())
            .as("the record must be untouched after a refused transition")
            .isEqualTo("Pending approval");
    assertThat(pageText()).contains("Not sent");
}
```

The unit tests prove the rule in the service layer. This proves it survives the HTTP
layer, the templates and the browser — including a request that deliberately bypasses the
UI by building and submitting a form from JavaScript.

---

## 2. Test Data

| Source | Used by | Why |
|---|---|---|
| **24 seeded demonstration offers** (`DataSeeder`) | J1, J3, J4, J5 | Deterministic, varied across all 7 statuses, 4 segments and all 4 risk bands — enough to exercise pagination, filters and the dashboard |
| **A customer created by the suite** (`Selenium Test Customer`, `ACC-99001`, risk 84, 18% discount) | J2 | Exercises create end to end without depending on seeded state |
| **A fresh database per run** | all | `--spring.datasource.url=…/target/selenium-data/retention`. Every `mvn verify` starts from the same 24 records, so the suite cannot pass only because a previous run left the right data behind |

---

## 3. How the Suite Runs

### 3.1 Build wiring

| Concern | Decision |
|---|---|
| Unit vs UI | Surefire excludes `**/*IT.java`; Failsafe includes only `**/*IT.java`. `mvn test` stays fast, `mvn verify` is the full gate |
| The application under test | `spring-boot:start` / `spring-boot:stop` bound to `pre-` / `post-integration-test`. `mvn verify` is **self-contained** — it does not depend on someone having remembered to start the app |
| Port | `8095`, separate from development (`8070`), Tomcat (`9090`) and the container (`8090`) |
| Driver | Selenium Manager resolves and downloads a matching `chromedriver` — no binary committed, no manual install |
| Headless | `selenium.headless=true` by default; set `-Dselenium.headless=false` to watch the browser drive itself |
| Target | `selenium.base.url` is a property, so the same suite runs against the throwaway instance locally and against a deployed server in Jenkins |

### 3.2 Running it

```bash
. scripts/env.sh
mvn clean verify                    # unit + Selenium
mvn clean verify -Dselenium.headless=false   # watch it run
mvn clean verify -Dit.test='J4_ApprovalWorkflowIT'   # one journey
```

---

## 4. Failure Screenshot Mechanism

A failing UI test is useless without a picture of the page. The base class registers a
JUnit 5 `TestWatcher` that fires on **every** failure:

```java
@RegisterExtension
final TestWatcher screenshotOnFailure = new TestWatcher() {
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        capture(context.getRequiredTestClass().getSimpleName()
                + "." + context.getRequiredTestMethod().getName());
    }
};
```

It writes a timestamped PNG into `target/selenium-screenshots/` and prints a banner into
the build log so the path is visible in Jenkins:

```
---------------------------------------------------------------
SELENIUM FAILURE SCREENSHOT
  test : J5_DashboardIT.kpiTilesArePopulated
  url  : http://localhost:8095/dashboard
  file : …/target/selenium-screenshots/FAILED-J5_DashboardIT.kpiTilesArePopulated-20261018-011823.png
---------------------------------------------------------------
```

Demonstrated by temporarily asserting a KPI tile labelled `CHURN RATE` that does not
exist. Evidence: `docs/screenshots/task-09-failure-screenshot.png`.

### The mechanism earned its keep immediately

The first run of the suite produced **27 errors**, all of them *"waiting for url to
contain /dashboard"*. The stack traces said only that sign-in had not completed.

The failure screenshot said why in one look: the **username field was empty** (showing its
placeholder) while the password field was filled and still focused. Chrome's
*save password* bubble was appearing after the first successful sign-in and stealing focus
from the form on every later visit.

Two fixes followed:

```java
// Chrome's password manager is the enemy of a login test
options.addArguments("--disable-save-password-bubble",
        "--disable-features=PasswordManagerEnableAccountStorage,"
                + "AutofillServerCommunication,PasswordLeakDetection,…");
prefs.put("credentials_enable_service", false);
prefs.put("profile.password_manager_enabled", false);
```

and `signIn` now **reads the field values back** before submitting, so a dropped keystroke
fails loudly at the cause instead of quietly 15 seconds later.

---

## 5. The Second Defect the Suite Found — in the tests themselves

After the login fix, 11 tests still failed with values that were *one step behind*:

```
J4_ApprovalWorkflowIT.managerApproves   expected: "Approved"  but was: "Pending approval"
J3 filtersSurvivePagination             url …?q=&status=&segment=&minRisk=&page=1
```

**Cause.** The application renders every page on the server and ships **no client-side
JavaScript**, so any meaningful click is a *navigation*. Selenium's `click()` returns as
soon as the event is dispatched, so the assertions were reading the **old page**.

**Fix — remove the race at its source, not with sleeps:**

```java
protected void click(By locator) {
    WebElement oldDocument = driver.findElement(By.tagName("html"));
    wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    awaitNavigation(oldDocument);           // staleness, then document.readyState
}
```

Chrome occasionally swaps the document before staleness can be observed, reporting
`Node with given id does not belong to the document`; that is caught and treated as
"the navigation already happened".

This is exactly the flakiness risk **R2** identified in Task 1, met with explicit waits
rather than `Thread.sleep`.

---

## 6. Local Execution Result

```
$ mvn clean verify

--- unit tests (surefire) ---
Tests run: 17, Failures: 0, Errors: 0 -- in SC1 - an offer reaches a customer only through an approval
Tests run:  4, Failures: 0, Errors: 0 -- in OfferTypeFormatTest
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0

--- integration tests (failsafe) : the Selenium journeys ---
Tests run: 6, Failures: 0, Errors: 0, Time elapsed:  4.310 s -- in J1 - Sign in, role-based access and sign out
Tests run: 5, Failures: 0, Errors: 0, Time elapsed:  5.748 s -- in J2 - Create a retention offer
Tests run: 8, Failures: 0, Errors: 0, Time elapsed:  9.420 s -- in J3 - Search and filter the offer pipeline
Tests run: 7, Failures: 0, Errors: 0, Time elapsed: 10.48 s -- in J4 - Submit, approve and send a retention offer
Tests run: 6, Failures: 0, Errors: 0, Time elapsed:  3.999 s -- in J5 - Summary dashboard
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0

BUILD SUCCESS
Total time:  42.527 s
```

### HTML report

`mvn surefire-report:failsafe-report-only` renders `target/reports/failsafe.html`:

| | Tests | Errors | Failures | Skipped | Success rate | Time |
|---|---|---|---|---|---|---|
| **Total** | **32** | 0 | 0 | 0 | **100%** | 31.28 s |
| J4 — Submit, approve and send | 7 | 0 | 0 | 0 | 100% | 10.00 s |
| J3 — Search and filter | 8 | 0 | 0 | 0 | 100% | 8.935 s |
| J1 — Sign in and access | 6 | 0 | 0 | 0 | 100% | 3.584 s |
| J5 — Summary dashboard | 6 | 0 | 0 | 0 | 100% | 3.505 s |
| J2 — Create an offer | 5 | 0 | 0 | 0 | 100% | 5.252 s |

Screenshot: `docs/screenshots/task-09-test-report.png`.

---

## 7. Verification against the acceptance criteria

| Criterion (US-13) | Result |
|---|---|
| AC1 — ≥ 4 critical journeys with meaningful assertions | ✅ **5 journeys, 32 tests** |
| AC2 — runs locally through Maven | ✅ `mvn clean verify`, self-contained |
| AC3 — a failing test captures a screenshot | ✅ §4, demonstrated |
| AC4 — results published, failure stops deployment | ⏭ Task 10 |
| AC5 — deliberate defect caught, fixed, rerun green | ⏭ Task 10 |

---

## Deliverable checklist for Task 9

- [x] Test plan — journeys chosen with a stated reason — §1
- [x] 3–5 critical user journeys identified — §1 (five)
- [x] Selenium WebDriver scripts with assertions — `src/test/java/com/crop/retention/selenium/`
- [x] Test data documented — §2
- [x] Executed locally through Maven — §6
- [x] Local test report — §6, `target/reports/failsafe.html`
- [x] Failure screenshot mechanism — §4, with a real diagnosis it produced
- [x] Evidence screenshots — `task-09-maven-verify.png`, `task-09-test-report.png`, `task-09-failure-screenshot.png`
