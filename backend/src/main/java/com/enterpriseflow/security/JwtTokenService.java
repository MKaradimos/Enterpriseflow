package com.enterpriseflow.security;

import com.enterpriseflow.user.RoleName;
import com.enterpriseflow.user.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed (HS256) access tokens. The subject is the user id; email and roles are added as claims so
 * that requests can be authorized without a database lookup.
 */
@Service
public class JwtTokenService {

    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates a short-lived access token for the user with their current roles. */
    public AccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        // Only identifiers and roles: a JWT is signed, not encrypted, so anyone holding it can read the claims.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim(ROLES_CLAIM, user.roleNames().stream().map(RoleName::name).sorted().toList())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }

    /** The encoded token and its expiry, which the SPA uses to know when to refresh. */
    public record AccessToken(String value, Instant expiresAt) {
    }

    /** Visible for tests that need a token with arbitrary claims (e.g. already expired). */
    public String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** The configured issuer; tokens with any other issuer are rejected on validation. */
    public String issuer() {
        return properties.issuer();
    }
}
