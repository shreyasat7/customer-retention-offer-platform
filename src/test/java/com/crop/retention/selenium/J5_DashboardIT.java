package com.crop.retention.selenium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 5 — A manager reads retention performance off the dashboard.
 *
 * <p>Critical because the dashboard is the only thing the business sees. If the numbers
 * are wrong or the page does not render, the product has no reporting value even if every
 * other screen works.</p>
 *
 * <p>Covers US-09 AC1–AC5.</p>
 */
@DisplayName("J5 - Summary dashboard")
class J5_DashboardIT extends BaseSeleniumIT {

    @Test
    @DisplayName("the dashboard is the landing page after signing in")
    void dashboardIsTheLandingPage() {
        signIn("manager", "manager123");

        assertThat(driver.getCurrentUrl()).endsWith("/dashboard");
        assertOnPage("Dashboard");
    }

    @Test
    @DisplayName("the four KPI tiles are present and carry real figures")
    void kpiTilesArePopulated() {
        signIn("manager", "manager123");

        var labels = driver.findElements(By.cssSelector(".kpi .label"))
                .stream().map(e -> e.getText().trim()).toList();
        assertThat(labels).containsExactly(
                "TOTAL OFFERS", "AWAITING APPROVAL", "ACCEPTANCE RATE", "MONTHLY REVENUE RETAINED");

        var values = driver.findElements(By.cssSelector(".kpi .value"));
        assertThat(values).hasSize(4);

        int totalOffers = Integer.parseInt(values.get(0).getText().trim());
        assertThat(totalOffers).as("the seeded book").isGreaterThanOrEqualTo(24);

        assertThat(values.get(2).getText()).endsWith("%");
        assertThat(values.get(3).getText()).startsWith("₹");
    }

    @Test
    @DisplayName("the status funnel shows all seven workflow states")
    void statusFunnelCoversEveryState() {
        signIn("manager", "manager123");

        var rows = driver.findElements(By.cssSelector(".funnel-row .nm"))
                .stream().map(e -> e.getText().trim()).toList();

        assertThat(rows).containsExactly(
                "Draft", "Pending approval", "Approved", "Rejected",
                "Sent to customer", "Accepted", "Declined");

        var counts = driver.findElements(By.cssSelector(".funnel-row .ct"));
        int sum = counts.stream().mapToInt(e -> Integer.parseInt(e.getText().trim())).sum();
        int total = Integer.parseInt(
                driver.findElements(By.cssSelector(".kpi .value")).get(0).getText().trim());

        assertThat(sum).as("the funnel must account for every offer").isEqualTo(total);
    }

    @Test
    @DisplayName("the churn-risk breakdown covers the four bands and totals 100%")
    void riskBreakdownIsComplete() {
        signIn("manager", "manager123");

        var bands = driver.findElements(By.cssSelector(".legend .row .nm"))
                .stream().map(e -> e.getText().trim()).toList();
        assertThat(bands).containsExactly("Critical", "High", "Medium", "Low");

        var counts = driver.findElements(By.cssSelector(".legend .row .v"));
        int sum = counts.stream().mapToInt(e -> Integer.parseInt(e.getText().trim())).sum();
        int total = Integer.parseInt(
                driver.findElements(By.cssSelector(".kpi .value")).get(0).getText().trim());

        assertThat(sum).isEqualTo(total);
    }

    @Test
    @DisplayName("the needs-attention panel lists offers awaiting a decision, oldest first")
    void needsAttentionIsOrderedOldestFirst() {
        signIn("manager", "manager123");

        var ages = driver.findElements(By.cssSelector(".attn .age b"))
                .stream().map(e -> Integer.parseInt(e.getText().trim())).toList();

        assertThat(ages).isNotEmpty();
        assertThat(ages).as("oldest first").isSortedAccordingTo((a, b) -> Integer.compare(b, a));
        assertThat(ages).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("the dashboard renders in under two seconds")
    void dashboardRendersQuickly() {
        signIn("manager", "manager123");

        long start = System.currentTimeMillis();
        open("/dashboard");
        waitFor(By.cssSelector(".legend .row .v"));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).as("success criterion SC4").isLessThan(2000L);
    }
}
