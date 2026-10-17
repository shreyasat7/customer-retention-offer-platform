# Task 10 — Continuous Testing in Jenkins

**Gate stage:** `Selenium regression gate`, between *Package* and *Deploy to Tomcat*
**Evidence:** build **#6 FAILURE** (deploy skipped) → defect corrected → build **#7 SUCCESS**
**Backlog item:** M13 · **Issue:** [#13](https://github.com/shreyasat7/customer-retention-offer-platform/issues/13)

---

## 1. Integrating the Suite into the Pipeline

```groovy
stage('Selenium regression gate') {
    steps {
        sh """
            mvn -B verify \
                -Dselenium.headless=true \
                -Dselenium.port=${SELENIUM_PORT}
        """
    }
    post {
        always {
            junit testResults: 'target/failsafe-reports/*.xml', allowEmptyResults: true
            sh 'mvn -B surefire-report:failsafe-report-only || true'
            publishHTML(target: [
                reportDir: 'target/reports', reportFiles: 'failsafe.html',
                reportName: 'Selenium regression report',
                keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true])
        }
        failure {
            archiveArtifacts artifacts: 'target/selenium-screenshots/*.png',
                             allowEmptyArchive: true
            echo 'Selenium gate FAILED - the deploy stage will not run.'
        }
    }
}
```

| Decision | Reason |
|---|---|
| The stage runs plain `mvn -B verify` | `spring-boot:start` / `stop` are already bound to the integration-test phases, so the pipeline runs the *same command a developer runs locally* rather than a hand-assembled list of plugin goals that could drift |
| `junit` in `post { always }` | Results are published even when the stage fails — which is exactly when they matter |
| `publishHTML` | A reviewer can read the full run from the job sidebar without opening a console log |
| Screenshots archived on `failure` only | A green run has nothing to look at; a red one needs the picture |
| `SELENIUM_PORT` is a parameter | The throwaway instance can be moved if 8095 is taken on another agent |

---

## 2. How the Gate Stops a Deployment

The stage sits **between Package and Deploy**. Declarative Pipeline marks the build
`FAILURE` as soon as a stage fails and skips every stage below it — so the deploy is not
"cancelled", it is **never reached**.

```
Checkout → Build → Unit tests → Package → ✗ Selenium gate ─╳─ Deploy → Health check
                                                            │
                                               build FAILURE, server untouched
```

This is success criterion **SC8**.

---

## 3. The Deliberate Defect

### 3.1 What was broken

Commit `793741b` — *refactor(search): simplify the minimum-risk normalisation* — inverted
a ternary in `OfferService.search`:

```java
before:  Integer risk = (minRisk == null || minRisk <= 0) ? null : minRisk;
after:   Integer risk = (minRisk != null && minRisk <= 0) ? minRisk : null;
```

For every realistic input the result is `null`, so the minimum-risk filter is **silently
ignored**: the control still renders, the chip still appears, the query returns everything.

This shape of defect is deliberate. The service still compiles, still returns a `Page`,
and `OfferWorkflow` is untouched — **no unit test can see it.** Only a test that drives
the real search screen can.

### 3.2 The first attempt slipped through — and that was the more useful finding

Build **#5** built the defect commit `793741b` and finished **SUCCESS**.

The reason was in the test, not the pipeline. J3 asserted only that *every returned row
satisfied the filter*:

```java
assertThat(risks.stream().map(...)).allMatch(r -> r >= 70);
```

With the filter inert the query returned all 24 records — but the four seeded
`PENDING_APPROVAL` offers happen to sit at **72, 79, 81 and 91**. "Every row is at or
above 70" was true of the unfiltered list too. **The assertion could not distinguish
filtering from not filtering.**

### 3.3 Strengthening the assertion

Commit `22a234e` added a test that measures the *effect* of the filter rather than a
property the data satisfies anyway:

```java
@Test
@DisplayName("the minimum-risk filter actually excludes lower-risk offers")
void minimumRiskFilterExcludesLowerRiskOffers() {
    int unfiltered = totalResults();
    assertThat(unfiltered).isGreaterThanOrEqualTo(24);

    type(By.id("minRisk"), "85");      // only a handful of seeded offers reach 85
    click(By.id("apply-filters"));

    int filtered = totalResults();
    assertThat(filtered).as("a filter that excludes nothing is not filtering")
                        .isLessThan(unfiltered);
    assertThat(filtered).as("but it must not exclude everything").isGreaterThan(0);
    assertThat(risks).allMatch(r -> r >= 85);
}
```

`filtersCombine` got the same before/after count check.

> **The lesson this records:** a UI assertion that is satisfied by the fixture rather than
> by the behaviour is not a test. Deliberately breaking the feature is how that was found.

---

## 4. Build #6 — the gate stops the deployment

```
[Pipeline] { (Selenium regression gate)
---------------------------------------------------------------
SELENIUM FAILURE SCREENSHOT
  test : J3_SearchAndFilterIT.minimumRiskFilterExcludesLowerRiskOffers
  url  : http://localhost:8095/offers?q=&status=&segment=&minRisk=85&page=0
  file : …/workspace/crop-pipeline/target/selenium-screenshots/
         FAILED-J3_SearchAndFilterIT.minimumRiskFilterExcludesLowerRiskOffers-20261018-012706.png
---------------------------------------------------------------
[ERROR] Tests run: 9, Failures: 1, Errors: 0 <<< FAILURE! -- in J3 - Search and filter the offer pipeline
[ERROR]   J3_SearchAndFilterIT.minimumRiskFilterExcludesLowerRiskOffers:114
          [a filter that excludes nothing is not filtering]
[ERROR] Tests run: 33, Failures: 1, Errors: 0, Skipped: 0

Selenium gate FAILED - the deploy stage will not run.

[Pipeline] { (Deploy to Tomcat)
Stage "Deploy to Tomcat" skipped due to earlier failure(s)
[Pipeline] { (Health check)
Stage "Health check" skipped due to earlier failure(s)

FAILURE — 22a234e was NOT deployed. The running server is untouched.
Finished: FAILURE
```

### Stage view

| Stage | #5 (defect, weak test) | #6 (defect, strong test) | #7 (fixed) |
|---|---|---|---|
| Checkout / Build / Unit tests / Package | ✅ | ✅ | ✅ |
| **Selenium regression gate** | ✅ 58 s *(missed it)* | ❌ **57 s failed** | ✅ 58 s |
| **Deploy to Tomcat** | ✅ 828 ms | ❌ **skipped** | ✅ |
| **Health check** | ✅ 808 ms | ❌ **skipped** | ✅ |
| Result | SUCCESS | **FAILURE** | SUCCESS |

Note the unit tests are green in all three columns — they never saw this defect. That is
the argument for continuous *UI* testing in one table.

### Artefacts published by the failed build

| Artefact | Where |
|---|---|
| Failure screenshot | Build #6 → Artifacts → `target/selenium-screenshots/FAILED-…png` |
| JUnit results, 1 failure of 33 | Build #6 → Test Result |
| Selenium regression report (HTML) | Job sidebar → *Selenium regression report* |
| Full console log | `docs/logs/task-10-failed-build-console.log` |

---

## 5. Defect Correction and Successful Rerun

Commit `f2b4ec0` — *fix(search): restore the minimum-risk filter*:

```java
// (minRisk != null && minRisk <= 0) ? minRisk : null  was the defect: inverted,
// it returned null for every realistic value and the filter did nothing.
Integer risk = (minRisk == null || minRisk <= 0) ? null : minRisk;
```

The broken expression is kept as a comment above the fix — the mistake is easy to
reintroduce while tidying, and a comment is cheaper than finding it twice.

### Build #7

```
[INFO] Tests run: 6, Failures: 0 -- in J1 - Sign in, role-based access and sign out
[INFO] Tests run: 6, Failures: 0 -- in J5 - Summary dashboard
[INFO] Tests run: 9, Failures: 0 -- in J3 - Search and filter the offer pipeline
[INFO] Tests run: 7, Failures: 0 -- in J4 - Submit, approve and send a retention offer
[INFO] Tests run: 5, Failures: 0 -- in J2 - Create a retention offer
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0

[Pipeline] { (Deploy to Tomcat)
[Pipeline] { (Health check)
Healthy after 12s
SUCCESS — f2b4ec0 deployed to http://localhost:9090/crop
Finished: SUCCESS
```

Full log: `docs/logs/task-10-rerun-success-console.log`.

---

## 6. Build History

| # | Commit | Gate | Deploy | Result |
|---|---|---|---|---|
| 4 | `d1388dc` make the Selenium suite a deployment gate | ✅ 32 tests | ✅ | SUCCESS |
| 5 | `793741b` **defect introduced** | ✅ 32 tests *(assertion too weak)* | ✅ | SUCCESS |
| 6 | `22a234e` **assertion strengthened** | ❌ 1 of 33 failed | **skipped** | **FAILURE** |
| 7 | `f2b4ec0` **defect corrected** | ✅ 33 tests | ✅ | SUCCESS |

---

## 7. Verification against the acceptance criteria

| Criterion (US-13) | Result |
|---|---|
| AC2 — the suite runs headlessly in Jenkins | ✅ Builds #4–#7, headless Chrome on the agent |
| AC3 — a failing test captures a screenshot | ✅ Archived on build #6 |
| AC4 — results published; a failure marks the build FAILURE and stops the deploy | ✅ Build #6: deploy and health check skipped |
| AC5 — a deliberate defect is caught, fixed, and the rerun is green | ✅ #5 → #6 → #7 |

Success criteria closed: **SC8** (failing test blocks deployment) and **SC11** (≥ 4
journeys under automated test — five, 33 tests).

---

## Deliverable checklist for Task 10

- [x] Selenium suite integrated into the Jenkins pipeline — §1
- [x] Test reports published (JUnit trend + HTML) — §1, §4
- [x] Pipeline configured so failed tests stop deployment — §2, proven in §4
- [x] Failed pipeline evidence — §4, `docs/logs/task-10-failed-build-console.log`
- [x] Deliberately introduced defect corrected — §3, §5
- [x] Successful rerun — §5, `docs/logs/task-10-rerun-success-console.log`
- [x] Evidence screenshots — `task-10-pipeline-failed.png`, `task-10-failed-test-report.png`, `task-10-archived-failure-screenshot.png`, `task-10-pipeline-recovered.png`, `task-10-html-report.png`, `task-10-test-results-jenkins.png`
