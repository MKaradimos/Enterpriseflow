package com.enterpriseflow.auth;

import com.enterpriseflow.user.UserCredentialsInvalidatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bridges the user module and the auth module: when a user's credentials become invalid, all of their
 * refresh tokens are revoked. The user module publishes an event and does not depend on auth classes.
 */
@Component
class SessionRevocationListener {

    private final RefreshTokenService refreshTokenService;

    SessionRevocationListener(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * A plain @EventListener runs synchronously inside the publisher's transaction, so the revocation commits
     * or rolls back together with the deactivation / password change that caused it.
     */
    @EventListener
    void onCredentialsInvalidated(UserCredentialsInvalidatedEvent event) {
        refreshTokenService.revokeAllForUser(event.userId());
    }
}
