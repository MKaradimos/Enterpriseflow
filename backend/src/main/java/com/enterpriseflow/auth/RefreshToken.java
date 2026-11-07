package com.enterpriseflow.auth;

import com.enterpriseflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token as stored in the database. Only the SHA-256 hash is kept, never the raw token.
 * Tokens issued from the same login share a {@code familyId}: each refresh revokes the current token and
 * records its successor in {@code replacedById}. Reusing a revoked token revokes the whole family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private Instant expiresAt;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected RefreshToken() {
        // Required by JPA
    }

    RefreshToken(User user, String tokenHash, UUID familyId, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Marks the token as used or revoked; {@code replacedBy} is null when it was revoked without rotation. */
    void revoke(Instant now, UUID replacedBy) {
        this.revokedAt = now;
        this.replacedById = replacedBy;
    }

    UUID getId() {
        return id;
    }

    User getUser() {
        return user;
    }

    UUID getFamilyId() {
        return familyId;
    }
}
