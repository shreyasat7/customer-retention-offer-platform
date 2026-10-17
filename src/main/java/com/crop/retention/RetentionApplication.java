package com.crop.retention;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Customer Retention Offer Platform (CROP).
 *
 * <p>The application is packaged as a WAR so that a single artefact can be both
 * executed directly ({@code java -jar crop.war}, used by the Docker image) and
 * deployed to an external Tomcat server (used by the Jenkins deploy stage).</p>
 */
@SpringBootApplication
public class RetentionApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetentionApplication.class, args);
    }
}
