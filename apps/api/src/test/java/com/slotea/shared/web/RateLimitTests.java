package com.slotea.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.slotea.TestcontainersConfiguration;

/** El filtro de verdad: va antes de Spring Security y responde 429 en ProblemDetail con Retry-After. */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void tooManyLoginsFromOneIpAreRejectedBeforeSecurity() {
        String ip = "203.0.113.50, 198.51.100.200";
        for (int i = 0; i < 10; i++) {
            // Sin CSRF: Spring Security las rechaza (403), pero ya han contado.
            assertThat(mvc.post().uri("/api/v1/auth/login").header("X-Forwarded-For", ip))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        assertThat(mvc.post().uri("/api/v1/auth/login").header("X-Forwarded-For", ip))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader("Retry-After", "6") // 10 por minuto: una ficha cada 6 s
                .bodyJson().extractingPath("$.detail").isEqualTo(RateLimitConfig.DETAIL);

        assertThat(mvc.post().uri("/api/v1/auth/login").header("X-Forwarded-For", "203.0.113.51, 198.51.100.200"))
                .as("otro visitante detrás de la misma IP de Vercel")
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void theRestOfTheApiIsNotAffected() {
        String ip = "203.0.113.60, 198.51.100.200";
        for (int i = 0; i < 11; i++) {
            mvc.post().uri("/api/v1/auth/login").header("X-Forwarded-For", ip).exchange();
        }

        assertThat(mvc.get().uri("/api/v1/ping").header("X-Forwarded-For", ip)).hasStatus(HttpStatus.OK);
    }
}
