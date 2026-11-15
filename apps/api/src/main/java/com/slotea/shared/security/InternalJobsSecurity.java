package com.slotea.shared.security;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /internal/**}: endpoints que llama un cron (no un navegador) con la cabecera {@code X-Jobs-Token}.
 *
 * <ul>
 * <li>Cadena propia y con prioridad: aquí no hay cookies, ni JWT, ni tenant.</li>
 * <li>Sin CSRF: el CSRF protege de peticiones que el navegador hace solo con las cookies del usuario;
 *     aquí la credencial es una cabecera que un navegador no añade por su cuenta.</li>
 * <li>No es {@code Authorization: Bearer}: el resource server intentaría leerlo como un JWT.</li>
 * <li>La comparación es en tiempo constante (sobre los SHA-256, que además igualan las longitudes).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class InternalJobsSecurity {

    static final String HEADER = "X-Jobs-Token";
    private static final Logger log = LoggerFactory.getLogger(InternalJobsSecurity.class);

    @Bean
    @Order(1)
    SecurityFilterChain internalJobsFilterChain(HttpSecurity http, JobsProperties properties,
            ProblemDetailSecurityHandler problems) {
        if (!properties.enabled()) {
            log.warn("JOBS_TOKEN is not set: /internal/jobs/* will always answer 401");
        }
        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(new JobsTokenFilter(properties), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("JOBS"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }

    /** Si la cabecera coincide con el secreto, la petición queda autenticada como {@code ROLE_JOBS}. */
    private static final class JobsTokenFilter extends OncePerRequestFilter {

        private final JobsProperties properties;

        JobsTokenFilter(JobsProperties properties) {
            this.properties = properties;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String given = request.getHeader(HEADER);
            if (properties.enabled() && given != null && matches(given, properties.token())) {
                var context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "jobs", null, List.of(new SimpleGrantedAuthority("ROLE_JOBS"))));
                SecurityContextHolder.getContextHolderStrategy().setContext(context);
            }
            chain.doFilter(request, response);
        }

        private static boolean matches(String given, String expected) {
            return MessageDigest.isEqual(OpaqueTokens.sha256(given.strip()), OpaqueTokens.sha256(expected));
        }
    }
}
