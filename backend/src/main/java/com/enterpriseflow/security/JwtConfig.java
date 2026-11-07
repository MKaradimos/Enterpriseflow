package com.enterpriseflow.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 signing with a symmetric key: the same service issues and validates tokens, so there is no need
 * for a key pair. With an external identity provider (e.g. Keycloak) this would switch to RS256 and JWKS.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_KEY_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.jwt.secret must be Base64-encoded", e);
        }
        // Fail at startup rather than run with a key that can be brute-forced.
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must decode to at least 256 bits");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Default validators check exp/nbf (with 60s clock skew); the issuer check is added on top.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
