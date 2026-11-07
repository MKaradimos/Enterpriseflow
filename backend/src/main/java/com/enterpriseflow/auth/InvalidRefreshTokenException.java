package com.enterpriseflow.auth;

import org.springframework.security.core.AuthenticationException;

/** Missing, unknown, expired, revoked or reused refresh token. Always answered with a generic 401. */
public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
