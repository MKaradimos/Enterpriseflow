package com.enterpriseflow.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param registrationEnabled whether anyone may self-register (as EMPLOYEE); disabled in production,
 *                            where administrators create accounts
 * @param refreshTokenTtl     lifetime of a refresh token; each rotation issues a new one
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(boolean registrationEnabled,
                             @NotNull Duration refreshTokenTtl,
                             @Valid @NotNull RefreshCookie refreshCookie) {

    /**
     * @param secure must be true in production (HTTPS only). May be false for plain-HTTP local development.
     */
    public record RefreshCookie(@NotBlank String name, boolean secure) {
    }
}
