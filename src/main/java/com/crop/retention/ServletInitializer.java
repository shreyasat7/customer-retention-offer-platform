package com.crop.retention;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Bootstraps the application when the WAR is deployed into an external servlet
 * container (Apache Tomcat) instead of being run as an executable archive.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(RetentionApplication.class);
    }
}
