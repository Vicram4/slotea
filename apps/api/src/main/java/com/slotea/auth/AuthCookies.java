package com.slotea.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.slotea.shared.security.AuthClaims;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookies de sesión: {@code HttpOnly} (JavaScript no puede leerlas), {@code Secure} y
 * {@code SameSite=Lax}. Sin {@code Domain}: quedan ligadas al dominio de la web, que reenvía /api a
 * la API, así que son <em>first-party</em>.
 */
@Component
class AuthCookies {

    private final AuthProperties properties;

    AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    void write(HttpServletResponse response, AuthTokens tokens) {
        add(response, AuthClaims.ACCESS_COOKIE, tokens.accessToken(), "/", properties.accessTokenTtl());
        add(response, AuthClaims.REFRESH_COOKIE, tokens.refreshToken(), AuthClaims.REFRESH_COOKIE_PATH,
                properties.refreshTokenTtl());
    }

    void clear(HttpServletResponse response) {
        add(response, AuthClaims.ACCESS_COOKIE, "", "/", Duration.ZERO);
        add(response, AuthClaims.REFRESH_COOKIE, "", AuthClaims.REFRESH_COOKIE_PATH, Duration.ZERO);
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        var cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
