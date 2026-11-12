package com.slotea.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/** Un JWT_SECRET débil o mal copiado tiene que impedir que la API arranque. */
class JwtConfigTest {

    private static AuthProperties withSecret(String secret) {
        return new AuthProperties(secret, Duration.ofMinutes(15), Duration.ofDays(30), true);
    }

    @Test
    void rejectsSecretsShorterThan256Bits() {
        String short31 = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> new JwtConfig(withSecret(short31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsSecretsThatAreNotBase64() {
        assertThatThrownBy(() -> new JwtConfig(withSecret("not base64 at all!")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void acceptsA256BitSecret() {
        String ok = Base64.getEncoder().encodeToString(new byte[32]);
        assertThatCode(() -> new JwtConfig(withSecret(ok))).doesNotThrowAnyException();
    }
}
