package com.slotea.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.filter.ForwardedHeaderFilter;

import com.slotea.shared.web.RateLimiter.Limit;
import com.slotea.shared.web.RateLimiter.Rule;

/** Las reglas del limitador y de dónde sale la IP, sin Spring. */
class RateLimiterTest {

    static final Rule BOOKING = new Rule("booking", "POST", Pattern.compile("/api/v1/public/businesses/[^/]+/appointments"),
            new Limit(2, Duration.ofHours(1)), new Limit(3, Duration.ofHours(1)));

    @Test
    void eachIpHasItsOwnAllowance() {
        var limiter = new RateLimiter(List.of(BOOKING));
        String path = "/api/v1/public/businesses/barberia/appointments";

        assertThat(limiter.tryAcquire("POST", path, "198.51.100.1")).isEmpty();
        assertThat(limiter.tryAcquire("POST", path, "198.51.100.1")).isEmpty();
        var third = limiter.tryAcquire("POST", path, "198.51.100.1");
        assertThat(third).as("tercera reserva de la misma IP en una hora").isPresent();
        assertThat(third.getAsLong()).isBetween(1L, 3600L);
        assertThat(limiter.tryAcquire("POST", path, "198.51.100.2")).as("otra IP").isEmpty();
    }

    @Test
    void theGlobalLimitStopsSomeoneRotatingIps() {
        var limiter = new RateLimiter(List.of(BOOKING));
        String path = "/api/v1/public/businesses/barberia/appointments";

        for (int i = 1; i <= 3; i++) {
            assertThat(limiter.tryAcquire("POST", path, "198.51.100." + i)).isEmpty();
        }
        assertThat(limiter.tryAcquire("POST", path, "198.51.100.99")).isPresent();
    }

    @Test
    void onlyMatchingRequestsCount() {
        var limiter = new RateLimiter(List.of(BOOKING));

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("GET", "/api/v1/public/businesses/barberia/appointments", "198.51.100.1"))
                    .isEmpty();
            assertThat(limiter.tryAcquire("POST", "/api/v1/public/businesses/barberia/availability", "198.51.100.1"))
                    .isEmpty();
        }
    }

    @Test
    void theDefaultRulesCoverTheSensitiveEndpoints() {
        var limiter = new RateLimiter(RateLimiter.DEFAULT_RULES);
        String ip = "198.51.100.7";

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("POST", "/api/v1/auth/login", ip)).isEmpty();
        }
        assertThat(limiter.tryAcquire("POST", "/api/v1/auth/login", ip)).as("11.º login en un minuto").isPresent();
        assertThat(limiter.tryAcquire("GET", "/api/v1/me", ip)).as("el resto de la API sigue abierto").isEmpty();
    }

    @Test
    void throughVercelTheVisitorIsTheSecondToLastHop() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("169.254.1.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.200"); // [visitante, Vercel]

        assertThat(ClientIp.of(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void directlyToCloudRunTheOnlyHopIsTheClient() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("169.254.1.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(ClientIp.of(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void withoutTheHeaderItIsTheSocketAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.5");

        assertThat(ClientIp.of(request)).isEqualTo("192.0.2.5");
    }

    @Test
    void itReadsTheHeaderThatForwardedHeaderFilterHides() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.200");
        String[] seen = new String[2];

        new ForwardedHeaderFilter().doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(),
                (wrapped, _) -> {
                    var http = (jakarta.servlet.http.HttpServletRequest) wrapped;
                    seen[0] = http.getHeader("X-Forwarded-For");
                    seen[1] = ClientIp.of(http);
                });

        assertThat(seen[0]).as("Spring la oculta").isNull();
        assertThat(seen[1]).isEqualTo("203.0.113.10");
    }
}
