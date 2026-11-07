package com.enterpriseflow.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for refresh tokens. Package-private: only the auth module touches refresh tokens. */
interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Tokens are looked up by hash; the raw value presented by the client is hashed first. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Bulk updates bypass the persistence context. flushAutomatically writes pending entity changes first
    // (e.g. a user deactivated in the same transaction); without it, clearAutomatically would discard them.
    // clearAutomatically then evicts entities so nothing stale is read afterwards.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    // Ends every session of a user (deactivation, password or role change).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
