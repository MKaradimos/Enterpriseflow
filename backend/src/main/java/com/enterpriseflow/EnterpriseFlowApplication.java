package com.enterpriseflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot entry point. Component scanning starts from this package, so every feature module
 * (auth, user, project, task, approval, notification, audit, dashboard) is picked up automatically.
 * {@code @ConfigurationPropertiesScan} binds the typed {@code *Properties} records from application.yml.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EnterpriseFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseFlowApplication.class, args);
    }
}
