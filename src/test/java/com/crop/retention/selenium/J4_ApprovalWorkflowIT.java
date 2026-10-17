package com.crop.retention.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 4 — The end-to-end approval workflow across two roles.
 *
 * <p>This is the single most critical journey in the product. It is the business rule the
 * platform exists to enforce: <strong>no discount reaches a customer without a recorded
 * manager approval</strong> (objective B1, success criterion SC1). The unit tests prove
 * the rule in the service layer; this proves the rule survives all the way through the
 * HTTP layer, the templates and the browser.</p>
 *
 * <p>Covers US-07 AC1–AC5 and US-08 AC1–AC4.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("J4 - Submit, approve and send a retention offer")
class J4_ApprovalWorkflowIT extends BaseSeleniumIT {

    /** A DRAFT seeded record used as the subject of the whole journey. */
    private static final String DRAFT_CUSTOMER = "Sanya Kapoor";

    private static String offerUrl;

    @Test
    @Order(1)
    @DisplayName("an agent submits a draft for approval")
    void agentSubmitsDraft() {
        signIn("agent", "agent123");
        openDraftOffer();

        assertThat(currentStatus()).isEqualTo("Draft");
        assertThat(driver.findElements(By.id("action-submit"))).isNotEmpty();
        // an agent must not be able to approve
        assertThat(driver.findElements(By.id("action-approve"))).isEmpty();

        click(By.id("action-submit"));

        assertThat(waitFor(By.cssSelector(".alert--ok")).getText()).contains("Pending approval");
        assertThat(currentStatus()).isEqualTo("Pending approval");
        assertThat(pageText()).contains("Submitted for approval").contains("by agent");
    }

    @Test
    @Order(2)
    @DisplayName("the agent has nothing further to do and cannot send an unapproved offer")
    void agentCannotSendBeforeApproval() {
        signIn("agent", "agent123");
        driver.get(offerUrl);

        assertThat(currentStatus()).isEqualTo("Pending approval");
        assertThat(driver.findElements(By.id("action-send"))).isEmpty();
        assertThat(pageText()).contains("Nothing for your role to do here");
    }

    @Test
    @Order(3)
    @DisplayName("SC1 - sending an unapproved offer is refused even when the request is forged")
    void forgedSendIsRefused() {
        signIn("agent", "agent123");
        driver.get(offerUrl);

        String before = currentStatus();
        assertThat(before).isEqualTo("Pending approval");

        // the button is not rendered, so post the transition directly, the way a user
        // bypassing the UI would
        String id = offerUrl.substring(offerUrl.lastIndexOf('/') + 1);
        forgePost("/offers/" + id + "/send");

        assertThat(pageText()).contains("Action refused");
        assertThat(currentStatus())
                .as("the record must be untouched after a refused transition")
                .isEqualTo("Pending approval");
        assertThat(pageText()).contains("Not sent");
    }

    @Test
    @Order(4)
    @DisplayName("a manager sees the offer in the approvals queue and approves it")
    void managerApproves() {
        signIn("manager", "manager123");

        // the sidebar badge counts what is waiting
        assertThat(waitFor(By.cssSelector(".nav a .count")).getText()).isNotEmpty();

        driver.get(offerUrl);
        assertThat(driver.findElements(By.id("action-approve"))).isNotEmpty();
        assertThat(driver.findElements(By.id("action-reject"))).isNotEmpty();
        // a manager must not be able to send
        assertThat(driver.findElements(By.id("action-send"))).isEmpty();

        click(By.id("action-approve"));

        assertThat(currentStatus()).isEqualTo("Approved");
        assertThat(pageText()).contains("Manager decision")
                              .contains("Approved")
                              .contains("by manager");
    }

    @Test
    @Order(5)
    @DisplayName("the agent can now send the approved offer to the customer")
    void agentSendsApprovedOffer() {
        signIn("agent", "agent123");
        driver.get(offerUrl);

        assertThat(currentStatus()).isEqualTo("Approved");
        click(By.id("action-send"));

        assertThat(currentStatus()).isEqualTo("Sent to customer");
        assertThat(pageText()).contains("Sent to customer").contains("by agent");
    }

    @Test
    @Order(6)
    @DisplayName("the agent records the customer's answer and the offer reaches a terminal state")
    void agentRecordsOutcome() {
        signIn("agent", "agent123");
        driver.get(offerUrl);

        click(By.id("action-accept"));

        assertThat(currentStatus()).isEqualTo("Accepted");
        assertThat(pageText()).contains("Customer outcome");
        assertThat(pageText()).contains("terminal state");
        assertThat(driver.findElements(By.cssSelector("[id^=action-]"))).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("a rejection requires a written reason and sends the offer back to the agent")
    void rejectionRequiresAReason() {
        signIn("agent", "agent123");
        openDraftOfferFor("Priya Varma");
        click(By.id("action-submit"));
        String rejectedUrl = driver.getCurrentUrl();

        signIn("manager", "manager123");
        driver.get(rejectedUrl);
        click(By.id("action-reject"));              // comment box left empty

        assertThat(pageText()).contains("Action refused").contains("reason");
        assertThat(currentStatus()).isEqualTo("Pending approval");

        type(By.id("comment"), "22% exceeds the discount ceiling for the Plus segment.");
        click(By.id("action-reject"));

        assertThat(currentStatus()).isEqualTo("Rejected");
        assertThat(pageText()).contains("22% exceeds the discount ceiling");

        // the agent revises it back to a draft
        signIn("agent", "agent123");
        driver.get(rejectedUrl);
        click(By.id("action-revise"));
        assertThat(currentStatus()).isEqualTo("Draft");
    }

    // ----------------------------------------------------------------- helpers

    private void openDraftOffer() {
        openDraftOfferFor(DRAFT_CUSTOMER);
        offerUrl = driver.getCurrentUrl();
    }

    private void openDraftOfferFor(String customer) {
        open("/offers?q=" + customer.replace(" ", "+"));
        click(By.cssSelector("table.data tbody tr .ref"));
        waitFor(By.cssSelector(".card-head .pill"));
    }

    /**
     * Posts a workflow transition directly, carrying the CSRF token from the page, to
     * show that the server refuses it rather than the UI merely hiding the button.
     */
    private void forgePost(String path) {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("""
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = arguments[0];
                const existing = document.querySelector('input[name=_csrf]');
                if (existing) {
                    const token = document.createElement('input');
                    token.type = 'hidden'; token.name = '_csrf'; token.value = existing.value;
                    form.appendChild(token);
                }
                document.body.appendChild(form);
                form.submit();
                """, BASE_URL + path);
        waitFor(By.cssSelector(".card-head .pill"));
    }
}
