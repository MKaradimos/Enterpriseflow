package com.enterpriseflow.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret         Base64-encoded HMAC key, at least 256 bits. Supplied only through the environment.
 * @param issuer         value of the {@code iss} claim, verified on every request
 * @param accessTokenTtl lifetime of access tokens; kept short because they cannot be revoked
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {
}
