package com.slotea.shared.web;

import java.io.IOException;
import java.net.URI;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Límite de peticiones ({@link RateLimiter}) como filtro: va justo después del {@code ForwardedHeaderFilter}
 * y antes de Spring Security, así que una ráfaga se corta sin tocar la BD ni calcular un BCrypt.
 * Responde 429 en {@code ProblemDetail} con {@code Retry-After}.
 *
 * <p>Se desactiva con {@code app.rate-limit.enabled=false} (los tests lo hacen: todas sus peticiones
 * salen de la misma IP).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "app.rate-limit.enabled", matchIfMissing = true)
class RateLimitConfig {

    static final String DETAIL = "Demasiadas peticiones seguidas. Espera un momento y vuelve a intentarlo.";

    @Bean
    RateLimiter rateLimiter() {
        return new RateLimiter(RateLimiter.DEFAULT_RULES);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter, JsonMapper json) {
        var registration = new FilterRegistrationBean<>(new RateLimitFilter(limiter, json));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    static final class RateLimitFilter extends OncePerRequestFilter {

        private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

        private final RateLimiter limiter;
        private final JsonMapper json;

        RateLimitFilter(RateLimiter limiter, JsonMapper json) {
            this.limiter = limiter;
            this.json = json;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            OptionalLong retryAfter = limiter.tryAcquire(request.getMethod(), path, ClientIp.of(request));
            if (retryAfter.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }
            log.warn("Rate limit exceeded: {} {}", request.getMethod(), path);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, DETAIL);
            problem.setInstance(URI.create(path));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.getAsLong()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(response.getOutputStream(), problem);
        }
    }
}
