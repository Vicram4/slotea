package com.slotea.shared.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Para tests {@code @WebMvcTest}: la misma seguridad que en producción (rutas públicas, CSRF,
 * respuestas ProblemDetail) sin cargar el módulo de auth. Ningún JWT es válido.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
public class WebSliceSecurity {

    @Bean
    JwtDecoder rejectingJwtDecoder() {
        return token -> {
            throw new BadJwtException("No JWT is valid in web slice tests");
        };
    }
}
