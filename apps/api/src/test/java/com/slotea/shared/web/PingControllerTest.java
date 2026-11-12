package com.slotea.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.slotea.shared.security.WebSliceSecurity;

@WebMvcTest(PingController.class)
@Import(WebSliceSecurity.class)
class PingControllerTest {

    @Autowired
    MockMvcTester mvc;

    /** Sustituye el reloj real por uno fijo (lo crea el método estático {@link #clock()}). */
    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void returnsStatusServiceAndCurrentTime() {
        assertThat(mvc.get().uri("/api/v1/ping"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": "ok", "service": "slotea-api", "time": "2026-01-15T10:00:00Z"}
                        """);
    }

    @Test
    void unknownPublicRouteReturnsProblemDetail() {
        assertThat(mvc.get().uri("/api/v1/auth/does-not-exist"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType("application/problem+json");
    }

    /** Sin sesión no revelamos qué rutas protegidas existen: 401 antes que 404. */
    @Test
    void protectedRouteWithoutSessionReturnsUnauthorizedProblem() {
        assertThat(mvc.get().uri("/api/v1/does-not-exist"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType("application/problem+json")
                .hasHeader("WWW-Authenticate", "Bearer");
    }
}
