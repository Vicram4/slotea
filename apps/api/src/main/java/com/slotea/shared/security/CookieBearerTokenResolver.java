package com.slotea.shared.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Lee el JWT de la cookie {@code httpOnly}, no de la cabecera {@code Authorization}: el navegador
 * nunca ve el token.
 *
 * <p>En las rutas públicas no se lee: una cookie caducada o manipulada no debe impedir hacer login,
 * refrescar la sesión o ver la página de reservas (Spring respondería 401 al intentar validarla).
 */
final class CookieBearerTokenResolver implements BearerTokenResolver {

    private final RequestMatcher publicPaths;

    CookieBearerTokenResolver(RequestMatcher publicPaths) {
        this.publicPaths = publicPaths;
    }

    @Override
    public @Nullable String resolve(HttpServletRequest request) {
        if (publicPaths.matches(request) || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AuthClaims.ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
