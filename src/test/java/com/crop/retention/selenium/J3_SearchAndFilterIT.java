package com.crop.retention.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 3 — Find an existing offer before raising a second one for the same customer.
 *
 * <p>Critical because duplicate offers reaching the same customer is one of the pain
 * points the product exists to remove (P5). If search is wrong, agents go back to
 * guessing.</p>
 *
 * <p>Covers US-06 AC1–AC4 and US-03 AC2.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("J3 - Search and filter the offer pipeline")
class J3_SearchAndFilterIT extends BaseSeleniumIT {

    @BeforeEach
    void signInAsAgent() {
        signIn("agent", "agent123");
        open("/offers");
    }

    @Test
    @Order(1)
    @DisplayName("the unfiltered list is paginated ten per page")
    void listIsPaginated() {
        assertThat(driver.findElements(By.cssSelector("table.data tbody tr"))).hasSize(10);
        assertThat(waitFor(By.cssSelector(".pager")).getText()).contains("Showing 10 of");
        assertThat(driver.findElements(By.linkText("Next"))).isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("free text matches a customer name, case-insensitively and on a partial word")
    void searchByPartialCustomerName() {
        type(By.id("q"), "pill");          // partial, lower case, of "Rohan Pillai"
        click(By.id("apply-filters"));

        var rows = driver.findElements(By.cssSelector("table.data tbody tr"));
        assertThat(rows).isNotEmpty();
        assertThat(pageText()).contains("Rohan Pillai");
        assertThat(waitFor(By.cssSelector(".chips")).getText()).contains("text: pill");
    }

    @Test
    @Order(3)
    @DisplayName("free text matches an email address")
    void searchByEmail() {
        type(By.id("q"), "diya.k@example.com");
        click(By.id("apply-filters"));

        assertThat(driver.findElements(By.cssSelector("table.data tbody tr"))).hasSize(1);
        assertThat(pageText()).contains("Diya Krishnan");
    }

    @Test
    @Order(4)
    @DisplayName("free text matches a case reference")
    void searchByCaseReference() {
        type(By.id("q"), "CRO-0003");
        click(By.id("apply-filters"));

        assertThat(driver.findElements(By.cssSelector("table.data tbody tr"))).hasSize(1);
        assertThat(waitFor(By.cssSelector("table.data tbody tr .ref")).getText()).isEqualTo("CRO-0003");
    }

    @Test
    @Order(5)
    @DisplayName("status, segment and risk filters combine with each other")
    void filtersCombine() {
        select(By.id("status"), "Pending approval");
        type(By.id("minRisk"), "70");
        click(By.id("apply-filters"));

        var statuses = driver.findElements(By.cssSelector("table.data tbody tr .pill"));
        assertThat(statuses).isNotEmpty();
        assertThat(statuses.stream().map(e -> e.getText().trim()).distinct())
                .containsExactly("Pending approval");

        var risks = driver.findElements(By.cssSelector("table.data tbody tr .risk"));
        assertThat(risks.stream().map(e -> Integer.parseInt(e.getText().trim())))
                .allMatch(r -> r >= 70);

        String chips = waitFor(By.cssSelector(".chips")).getText();
        assertThat(chips).contains("status: Pending approval").contains("risk");
    }

    @Test
    @Order(6)
    @DisplayName("a search that matches nothing shows an empty state, not a blank table")
    void emptyStateWhenNothingMatches() {
        type(By.id("q"), "zzzz-no-such-customer-zzzz");
        click(By.id("apply-filters"));

        assertThat(waitFor(By.cssSelector(".empty h3")).getText())
                .isEqualTo("No offers match these filters");
        assertThat(driver.findElements(By.cssSelector("table.data"))).isEmpty();
        assertThat(driver.findElements(By.linkText("Clear filters"))).isNotEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("Reset clears every filter and returns the whole pipeline")
    void resetClearsFilters() {
        select(By.id("segment"), "Enterprise");
        click(By.id("apply-filters"));
        assertThat(waitFor(By.cssSelector(".chips")).getText()).contains("segment: Enterprise");

        click(By.id("reset-filters"));

        assertThat(driver.findElements(By.cssSelector(".chips"))).isEmpty();
        assertThat(waitFor(By.cssSelector(".pager")).getText()).contains("Showing 10 of");
    }

    @Test
    @Order(8)
    @DisplayName("active filters survive a move to the next page")
    void filtersSurvivePagination() {
        type(By.id("minRisk"), "40");
        click(By.id("apply-filters"));

        click(By.linkText("Next"));

        assertThat(driver.getCurrentUrl()).contains("minRisk=40").contains("page=1");
        assertThat(waitFor(By.cssSelector(".chips")).getText()).contains("risk");

        var risks = driver.findElements(By.cssSelector("table.data tbody tr .risk"));
        assertThat(risks.stream().map(e -> Integer.parseInt(e.getText().trim())))
                .allMatch(r -> r >= 40);
    }
}
