package com.crop.retention.selenium;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared Selenium plumbing: one headless Chrome per test class, explicit waits, the page
 * helpers every journey needs, and a screenshot captured automatically whenever a test
 * fails.
 *
 * <p>Configured entirely from system properties supplied by the Failsafe plugin, so the
 * same suite runs against the throwaway instance Maven starts on :8095 locally and
 * against a deployed server in Jenkins, without a code change.</p>
 */
abstract class BaseSeleniumIT {

    protected static final String BASE_URL =
            System.getProperty("selenium.base.url", "http://localhost:8095");
    private static final boolean HEADLESS =
            Boolean.parseBoolean(System.getProperty("selenium.headless", "true"));
    private static final Path SHOT_DIR =
            Paths.get(System.getProperty("selenium.screenshot.dir", "target/selenium-screenshots"));

    protected static WebDriver driver;
    protected static WebDriverWait wait;

    // ------------------------------------------------------------- lifecycle

    @BeforeAll
    static void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        if (HEADLESS) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1440,1000", "--no-sandbox",
                "--disable-dev-shm-usage", "--disable-gpu");

        // Chrome's password manager is the enemy of a login test: after the first
        // successful sign-in it offers to save the credential, and the bubble steals
        // focus from the form on every later visit. Turning the whole feature off keeps
        // the browser out of the way of the thing under test.
        options.addArguments("--disable-save-password-bubble",
                "--disable-autofill-keyboard-accessory-view",
                "--disable-features=PasswordManagerEnableAccountStorage,"
                        + "AutofillServerCommunication,PasswordLeakDetection,"
                        + "AutofillEnableAccountWalletStorage");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("autofill.profile_enabled", false);
        options.setExperimentalOption("prefs", prefs);

        // Selenium Manager resolves a matching chromedriver automatically
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterAll
    static void stopBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @BeforeEach
    void startSignedOut() {
        // every test begins from a clean session, so ordering never leaks a login
        open("/login");
        logout();
    }

    /**
     * Screenshot-on-failure. A failing UI test is useless without a picture of what the
     * page actually looked like, so every failure writes a timestamped PNG into
     * {@code target/selenium-screenshots/} and prints the path into the build log, where
     * Jenkins archives it.
     */
    @RegisterExtension
    final TestWatcher screenshotOnFailure = new TestWatcher() {
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            capture(context.getRequiredTestClass().getSimpleName()
                    + "." + context.getRequiredTestMethod().getName());
        }
    };

    private void capture(String name) {
        if (!(driver instanceof TakesScreenshot shooter)) {
            return;
        }
        try {
            Files.createDirectories(SHOT_DIR);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path target = SHOT_DIR.resolve("FAILED-" + name + "-" + stamp + ".png");
            Files.write(target, shooter.getScreenshotAs(OutputType.BYTES));

            System.out.println("""
                    ---------------------------------------------------------------
                    SELENIUM FAILURE SCREENSHOT
                      test : %s
                      url  : %s
                      file : %s
                    ---------------------------------------------------------------"""
                    .formatted(name, driver.getCurrentUrl(), target.toAbsolutePath()));
        } catch (IOException | WebDriverException ex) {
            System.out.println("Could not capture a failure screenshot: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------- page helpers

    protected void open(String path) {
        driver.get(BASE_URL + path);
    }

    /**
     * Signs in and waits until the dashboard is actually reached.
     *
     * <p>The field values are read back before the form is submitted. A browser that
     * silently drops a keystroke would otherwise post an empty username and fail later,
     * somewhere far from the real cause - which is exactly what happened the first time
     * this suite ran.</p>
     */
    protected void signIn(String username, String password) {
        logout();
        open("/login");
        waitFor(By.name("username"));

        for (int attempt = 1; attempt <= 3; attempt++) {
            type(By.name("username"), username);
            type(By.name("password"), password);

            String typedUser = driver.findElement(By.name("username")).getAttribute("value");
            String typedPass = driver.findElement(By.name("password")).getAttribute("value");
            if (username.equals(typedUser) && password.equals(typedPass)) {
                break;
            }
            if (attempt == 3) {
                throw new IllegalStateException(
                        "Could not enter the credentials: username field held '" + typedUser + "'");
            }
            open("/login");
            waitFor(By.name("username"));
        }

        click(By.id("signin"));
        wait.until(ExpectedConditions.urlContains("/dashboard"));
    }

    protected void logout() {
        driver.manage().deleteAllCookies();
    }

    protected WebElement waitFor(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Clicks, then waits for the page that click produced.
     *
     * <p>The application renders every page on the server and ships no client-side
     * JavaScript, so any meaningful click is a navigation. Selenium's {@code click()}
     * returns as soon as the event is dispatched, which means a naive assertion reads the
     * <em>old</em> page and reports a stale value - the first run of this suite failed
     * exactly that way, with an approved offer still showing "Pending approval".</p>
     *
     * <p>Waiting for the old document to go stale and for the new one to finish loading
     * removes the race at its source, rather than papering over it with sleeps.</p>
     */
    protected void click(By locator) {
        WebElement oldDocument = driver.findElement(By.tagName("html"));
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
        awaitNavigation(oldDocument);
    }

    /** Clicks without expecting a navigation (nothing in the MVP needs this yet). */
    protected void clickInPlace(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    private void awaitNavigation(WebElement oldDocument) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.stalenessOf(oldDocument));
        } catch (TimeoutException ignored) {
            // the click did not navigate; nothing to wait for
            return;
        } catch (WebDriverException replacedTooFast) {
            // The document was swapped before staleness could be observed. Chrome reports
            // this as "Node with given id does not belong to the document" rather than a
            // StaleElementReferenceException. It means the navigation already happened,
            // which is what we were waiting for.
        }
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
    }

    protected void type(By locator, String value) {
        WebElement field = waitFor(locator);
        field.clear();
        field.sendKeys(value);
    }

    protected void select(By locator, String visibleText) {
        new org.openqa.selenium.support.ui.Select(waitFor(locator)).selectByVisibleText(visibleText);
    }

    protected String pageText() {
        return driver.findElement(By.tagName("body")).getText();
    }

    protected Optional<WebElement> findOptional(By locator) {
        List<WebElement> found = driver.findElements(locator);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Status pill text on the currently open offer detail page. */
    protected String currentStatus() {
        return waitFor(By.cssSelector(".card-head .pill")).getText().trim();
    }

    protected void assertOnPage(String title) {
        assertThat(waitFor(By.cssSelector(".topbar h1")).getText()).isEqualTo(title);
    }
}
