package com.crop.retention.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 2 — An agent raises a retention offer for an at-risk customer.
 *
 * <p>Critical because it is the entry point of the whole product: if an offer cannot be
 * created, there is nothing to approve, send or report on.</p>
 *
 * <p>Covers US-02 AC1–AC3 and US-04 AC1.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("J2 - Create a retention offer")
class J2_CreateOfferIT extends BaseSeleniumIT {

    // ------------------------------------------------------------- test data
    private static final String CUSTOMER = "Selenium Test Customer";
    private static final String EMAIL    = "selenium.customer@example.com";
    private static final String ACCOUNT  = "ACC-99001";
    private static final String SEGMENT  = "Premium";
    private static final String RISK     = "84";
    private static final String MONTHLY  = "3200.00";
    private static final String TYPE     = "Discount %";
    private static final String VALUE    = "18";
    private static final String NOTES    = "Raised by the Selenium regression suite.";

    private static String createdCaseRef;

    @Test
    @Order(1)
    @DisplayName("an invalid form is redisplayed with field-level errors and nothing is saved")
    void invalidFormIsRejected() {
        signIn("agent", "agent123");

        // count first: countOffers() navigates to the list, so it cannot run while the
        // create form is open
        long before = countOffers();
        open("/offers/new");

        // submit completely empty
        click(By.id("save-offer"));

        assertThat(waitFor(By.cssSelector(".alert--bad")).getText())
                .contains("This offer was not saved");

        var errors = driver.findElements(By.cssSelector(".err-msg"));
        assertThat(errors).as("one message per required field").hasSizeGreaterThanOrEqualTo(6);
        assertThat(errors.stream().map(org.openqa.selenium.WebElement::getText).toList())
                .contains("Customer name is required", "Customer email is required");

        assertThat(countOffers()).as("nothing persisted").isEqualTo(before);
    }

    @Test
    @Order(2)
    @DisplayName("an invalid email is caught by bean validation, not only by the browser")
    void invalidEmailIsRejected() {
        signIn("agent", "agent123");
        open("/offers/new");

        fillValidForm();
        type(By.id("customerEmail"), "not-an-email");
        click(By.id("save-offer"));

        assertThat(pageText()).contains("Enter a valid email address");
        assertThat(driver.getCurrentUrl()).doesNotContain("/offers/1");
    }

    @Test
    @Order(3)
    @DisplayName("a churn risk outside 0-100 is refused")
    void outOfRangeRiskIsRejected() {
        signIn("agent", "agent123");
        open("/offers/new");

        fillValidForm();
        type(By.id("churnRisk"), "140");
        click(By.id("save-offer"));

        assertThat(pageText()).contains("Churn risk must be between 0 and 100");
    }

    @Test
    @Order(4)
    @DisplayName("a valid offer is created as a DRAFT with a generated case reference")
    void validOfferIsCreated() {
        signIn("agent", "agent123");
        open("/offers/new");

        fillValidForm();
        click(By.id("save-offer"));

        // lands on the detail page of the new record
        assertThat(waitFor(By.cssSelector(".alert--ok")).getText()).contains("created as a draft");

        createdCaseRef = waitFor(By.cssSelector(".topbar h1")).getText().trim();
        assertThat(createdCaseRef).matches("CRO-\\d{4}");

        assertThat(currentStatus()).isEqualTo("Draft");
        assertThat(pageText()).contains(CUSTOMER).contains(EMAIL).contains(ACCOUNT);
        assertThat(pageText()).contains("18%");           // formatted offer value
        assertThat(pageText()).contains("Critical risk"); // 84 -> Critical band
    }

    @Test
    @Order(5)
    @DisplayName("the new offer carries a complete audit trail and appears in the list")
    void newOfferIsAuditedAndListed() {
        signIn("agent", "agent123");

        open("/offers?q=" + CUSTOMER.replace(" ", "+"));
        assertThat(pageText()).contains(CUSTOMER).contains(createdCaseRef);

        click(By.cssSelector("table.data tbody tr .ref"));

        assertThat(pageText()).contains("Created").contains("by agent");
        assertThat(pageText()).contains("Not yet submitted");
        assertThat(pageText()).contains("Awaiting a manager decision");
    }

    // ----------------------------------------------------------------- helpers

    private void fillValidForm() {
        type(By.id("customerName"), CUSTOMER);
        type(By.id("customerEmail"), EMAIL);
        type(By.id("accountId"), ACCOUNT);
        select(By.id("segment"), SEGMENT);
        type(By.id("churnRisk"), RISK);
        type(By.id("monthlyValue"), MONTHLY);
        select(By.id("offerType"), TYPE);
        type(By.id("offerValue"), VALUE);
        type(By.id("notes"), NOTES);
    }

    /** Total record count, read from the list header. */
    private long countOffers() {
        open("/offers");
        String header = waitFor(By.cssSelector(".card-head .sub")).getText();
        return Long.parseLong(header.replaceAll("^(\\d+).*", "$1"));
    }
}
