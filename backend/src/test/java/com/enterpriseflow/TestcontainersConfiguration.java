package com.enterpriseflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Starts a real PostgreSQL for integration tests. Pinned to the same major version as docker-compose
 * so tests and runtime behave identically.
 *
 * <p>{@code @ServiceConnection} makes Spring Boot point the datasource at the container automatically,
 * so no URL or credentials need to be configured for tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
