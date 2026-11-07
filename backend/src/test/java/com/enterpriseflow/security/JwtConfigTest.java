package com.enterpriseflow.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** The application must refuse to start with a weak or malformed JWT signing key. */
class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void refusesKeysShorterThan256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> config.jwtSigningKey(properties(shortKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void refusesNonBase64Keys() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties("not base64 !!")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, "test", Duration.ofMinutes(5));
    }
}
