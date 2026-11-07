package com.enterpriseflow.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Injected instead of calling Instant.now() directly, so tests can control time (e.g. token expiry). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
