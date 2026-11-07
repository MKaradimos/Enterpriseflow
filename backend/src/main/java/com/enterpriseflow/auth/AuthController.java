package com.enterpriseflow.auth;

import com.enterpriseflow.auth.dto.AuthDtos.AuthResponse;
import com.enterpriseflow.auth.dto.AuthDtos.CsrfResponse;
import com.enterpriseflow.auth.dto.AuthDtos.LoginRequest;
import com.enterpriseflow.auth.dto.AuthDtos.RegisterRequest;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.user.UserService;
import com.enterpriseflow.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Login and refresh return the access token in the JSON body and set the refresh
 * token as an HttpOnly cookie, so JavaScript can never read the long-lived credential.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    /** The cookie is sent only to auth endpoints, never with ordinary API calls. */
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserService userService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, UserService userService, AuthProperties properties) {
        this.authService = authService;
        this.userService = userService;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token, required as X-XSRF-TOKEN header for /refresh and /logout")
    public CsrfResponse csrf(CsrfToken token) {
        // Reading the token makes Spring generate it and set the matching cookie on this response.
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/register")
    @Operation(summary = "Self-register as EMPLOYEE (only when registration is enabled)")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the refresh token)")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "${app.auth.refresh-cookie.name}", required = false)
                                                String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = "${app.auth.refresh-cookie.name}", required = false)
                                       String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "The authenticated user")
    public UserResponse me(CurrentUser user) {
        return userService.get(user.id());
    }

    /** 200 response with the access token in the body and the new refresh token in a cookie. */
    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.Session session) {
        ResponseCookie cookie = refreshCookie(session.refreshToken(), properties.refreshTokenTtl());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                // Token responses must never be cached by browsers or proxies.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(session.response());
    }

    /** Builds the refresh cookie; an empty value with max-age 0 deletes it on logout. */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.refreshCookie().name(), value)
                .httpOnly(true)
                .secure(properties.refreshCookie().secure())
                // Strict: never sent on cross-site requests, which blocks CSRF at the browser level as well.
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
