package com.enterpriseflow.auth;

import com.enterpriseflow.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 *
 * <p>Every refresh revokes the presented token and issues a new one in the same family. If a token that was
 * already rotated is presented again, it must have been copied by someone, so the whole family is revoked and
 * both the attacker and the legitimate user must log in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new token family (a new login). Returns the raw token; only its hash is stored. */
    @Transactional
    public String issue(User user) {
        return issueInFamily(user, UUID.randomUUID()).rawToken();
    }

    /**
     * {@code noRollbackFor}: when reuse is detected we revoke the family and then throw. Without this the
     * exception would roll back the revocation and the stolen token family would stay usable.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} tokens in family {}",
                    current.getUser().getId(), revoked, current.getFamilyId());
            throw new InvalidRefreshTokenException("Refresh token reuse detected");
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }
        User user = current.getUser();
        if (!user.isActive()) {
            throw new InvalidRefreshTokenException("User is deactivated");
        }

        IssuedToken next = issueInFamily(user, current.getFamilyId());
        current.revoke(now, next.entity().getId());
        return new Rotation(user.getId(), next.rawToken());
    }

    /** Logout. Unknown or already revoked tokens are ignored so logout is idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Signs the user out everywhere, e.g. after deactivation or a password change. */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    /** Generates 256 random bits, stores their hash with an expiry and returns the raw value for the cookie. */
    private IssuedToken issueInFamily(User user, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        RefreshToken entity = repository.save(
                new RefreshToken(user, hash(raw), familyId, now, now.plus(properties.refreshTokenTtl())));
        return new IssuedToken(raw, entity);
    }

    /**
     * Only the SHA-256 hash is stored, so a database leak does not expose usable tokens. A plain hash is
     * enough (unlike passwords) because the token has 256 bits of entropy and cannot be brute-forced.
     */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record Rotation(UUID userId, String rawToken) {
    }

    private record IssuedToken(String rawToken, RefreshToken entity) {
    }
}
