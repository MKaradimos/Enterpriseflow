package com.enterpriseflow.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Origins allowed to call the API from a browser.
 *
 * <p>Explicit origins are required: because credentials (the refresh-token cookie) are allowed,
 * the CORS spec forbids the {@code *} wildcard.
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
