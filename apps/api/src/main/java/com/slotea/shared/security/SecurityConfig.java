package com.slotea.shared.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * API sin estado: JWT de acceso en cookie {@code httpOnly}, CSRF con cookie legible por la SPA
 * ({@code csrf.spa()}) y permisos por rol con {@code @PreAuthorize}.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    /** Rutas sin sesión. La misma lista decide qué es público y dónde no se lee la cookie del JWT. */
    static final RequestMatcher PUBLIC_PATHS = new OrRequestMatcher(
            pathPattern("/api/v1/auth/**"),
            pathPattern("/api/v1/ping"),
            pathPattern("/actuator/health/**"),
            pathPattern("/v3/api-docs/**"),
            pathPattern("/swagger-ui/**"),
            pathPattern("/swagger-ui.html"),
            pathPattern("/error"));

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailSecurityHandler problems) {
        http
                .csrf(csrf -> csrf.spa())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(new CookieBearerTokenResolver(PUBLIC_PATHS))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .addFilterAfter(new TenantFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** El claim {@code role} se convierte en la autoridad {@code ROLE_OWNER} o {@code ROLE_STAFF}. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AuthClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** El dueño puede hacer todo lo que hace un empleado. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_OWNER > ROLE_STAFF");
    }

    /** BCrypt por defecto, con prefijo {@code {bcrypt}} para poder migrar de algoritmo en el futuro. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
