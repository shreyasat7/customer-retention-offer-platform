package com.crop.retention.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 1 — Sign in, see only what your role allows, sign out.
 *
 * <p>Critical because it is the gate on every other journey: if authentication or the
 * role-driven navigation breaks, nothing else in the product is reachable or safe.</p>
 *
 * <p>Covers US-01 AC1–AC4 and part of US-02 AC4.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("J1 - Sign in, role-based access and sign out")
class J1_LoginAndAccessIT extends BaseSeleniumIT {

    @Test
    @Order(1)
    @DisplayName("an unauthenticated request to a protected page is redirected to the login form")
    void protectedPagesRedirectToLogin() {
        open("/offers");

        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(waitFor(By.cssSelector(".login-box h1")).getText()).isEqualTo("Sign in");
    }

    @Test
    @Order(2)
    @DisplayName("invalid credentials are refused without saying which field was wrong")
    void invalidCredentialsAreRefused() {
        open("/login");
        type(By.name("username"), "agent");
        type(By.name("password"), "definitely-not-the-password");
        click(By.id("signin"));

        wait.until(ExpectedConditions.urlContains("error"));
        String message = waitFor(By.cssSelector(".alert--bad")).getText();

        assertThat(message).isEqualTo("Invalid username or password.");
        // the message must not reveal whether the username exists
        assertThat(message.toLowerCase()).doesNotContain("user not found").doesNotContain("wrong password");
        assertThat(driver.getCurrentUrl()).contains("/login");
    }

    @Test
    @Order(3)
    @DisplayName("an agent signs in and sees the agent-only controls")
    void agentSeesAgentControls() {
        signIn("agent", "agent123");

        assertOnPage("Dashboard");
        assertThat(waitFor(By.cssSelector(".side-user .who b")).getText()).isEqualTo("agent");
        assertThat(waitFor(By.cssSelector(".side-user .who span")).getText()).contains("AGENT");

        // "New offer" is an agent capability and must be offered to an agent
        assertThat(driver.findElements(By.linkText("New offer"))).isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("a manager does not see the agent-only New offer control")
    void managerDoesNotSeeAgentControls() {
        signIn("manager", "manager123");

        assertOnPage("Dashboard");
        assertThat(waitFor(By.cssSelector(".side-user .who b")).getText()).isEqualTo("manager");
        assertThat(driver.findElements(By.linkText("New offer"))).isEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("a manager requesting the create form directly is refused by the server")
    void managerCannotReachTheCreateFormDirectly() {
        signIn("manager", "manager123");

        // hiding the button is a convenience; the server is the control
        open("/offers/new");

        assertThat(pageText()).contains("403");
        assertThat(driver.findElements(By.id("save-offer"))).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("signing out invalidates the session and returns to the login page")
    void signOutEndsTheSession() {
        signIn("agent", "agent123");

        click(By.cssSelector(".side-user form button"));
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl()).contains("loggedOut");

        // the session really is gone, not just the page
        open("/dashboard");
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl()).contains("/login");
    }
}
