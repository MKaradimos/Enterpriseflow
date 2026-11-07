package com.enterpriseflow.auth.dto;

import com.enterpriseflow.user.dto.UserRequests;
import com.enterpriseflow.user.dto.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull @Pattern(regexp = UserRequests.PASSWORD_REGEX, message = UserRequests.PASSWORD_MESSAGE)
            String password) {
    }

    /**
     * The access token travels in the body and is kept in memory by the SPA. The refresh token is never in a
     * body; it is set as an HttpOnly cookie that JavaScript cannot read.
     */
    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

        public AuthResponse(String accessToken, Instant expiresAt, UserResponse user) {
            this(accessToken, "Bearer", expiresAt, user);
        }
    }

    public record CsrfResponse(String token, String headerName) {
    }
}
