package com.enterpriseflow.user;

import java.util.UUID;

/**
 * Published when a user's existing sessions must end (deactivation, password change).
 * The auth module listens and revokes refresh tokens, so the user module does not depend on auth internals.
 */
public record UserCredentialsInvalidatedEvent(UUID userId) {
}
