package com.enterpriseflow.auth;

import com.enterpriseflow.auth.dto.AuthDtos.AuthResponse;
import com.enterpriseflow.auth.dto.AuthDtos.LoginRequest;
import com.enterpriseflow.auth.dto.AuthDtos.RegisterRequest;
import com.enterpriseflow.common.error.ResourceNotFoundException;
import com.enterpriseflow.security.JwtTokenService;
import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserService;
import com.enterpriseflow.user.dto.UserResponse;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login / refresh / logout flow on top of Spring Security, JwtTokenService and RefreshTokenService. */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties properties;

    public AuthService(AuthenticationManager authenticationManager, UserService userService,
                       JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService,
                       AuthProperties properties) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
    }

    public UserResponse register(RegisterRequest request) {
        if (!properties.registrationEnabled()) {
            // 404 rather than 403: when disabled, the endpoint should look like it doesn't exist.
            throw new ResourceNotFoundException("Endpoint", "register");
        }
        return userService.registerEmployee(request.email(), request.fullName(), request.password());
    }

    /**
     * Delegates password checking to Spring Security (DaoAuthenticationProvider + BCrypt). Wrong password,
     * unknown email and disabled account all raise an AuthenticationException that becomes the same generic 401.
     */
    @Transactional
    public Session login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        User user = userService.findWithRoles(UUID.fromString(authentication.getName()));
        return startSession(user, refreshTokenService.issue(user));
    }

    /**
     * Deliberately not @Transactional: rotate() commits on its own, so a reuse-triggered family revocation
     * is persisted. Wrapping it in an outer transaction would roll that revocation back with the exception.
     */
    public Session refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Missing refresh token");
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        // Reloaded so the new access token carries the user's current roles.
        User user = userService.findWithRoles(rotation.userId());
        return startSession(user, rotation.rawToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private Session startSession(User user, String rawRefreshToken) {
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(user);
        return new Session(new AuthResponse(accessToken.value(), accessToken.expiresAt(), UserResponse.from(user)),
                rawRefreshToken);
    }

    /** What the controller needs: a response body plus the refresh token to put in the cookie. */
    public record Session(AuthResponse response, String refreshToken) {
    }
}
