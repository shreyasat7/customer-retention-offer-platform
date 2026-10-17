package com.crop.retention.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @Value("${spring.application.name:Customer Retention Offer Platform}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.database.label:H2 (file)}")
    private String database;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("appVersion", appVersion);
        model.addAttribute("serverPort", serverPort);
        model.addAttribute("database", database);
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        return "dashboard";
    }
}
