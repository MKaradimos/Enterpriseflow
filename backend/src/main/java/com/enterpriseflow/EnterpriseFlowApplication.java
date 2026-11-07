package com.enterpriseflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EnterpriseFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseFlowApplication.class, args);
    }
}
