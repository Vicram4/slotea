package com.slotea.support;

import java.util.UUID;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

/**
 * CSRF como lo hace la SPA: la cookie {@code XSRF-TOKEN} y la cabecera {@code X-XSRF-TOKEN} con el
 * mismo valor (<em>double submit cookie</em>).
 *
 * <p>No usamos {@code SecurityMockMvcRequestPostProcessors.csrf()}: sustituye para siempre el
 * repositorio de tokens del contexto de Spring (compartido entre tests) y la cookie real deja de
 * escribirse en los tests que se ejecuten después.
 */
public final class SpaCsrf {

    private SpaCsrf() {
    }

    public static RequestPostProcessor spaCsrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            Cookie[] existing = request.getCookies();
            Cookie[] cookies = existing == null ? new Cookie[1] : java.util.Arrays.copyOf(existing, existing.length + 1);
            cookies[cookies.length - 1] = new Cookie("XSRF-TOKEN", token);
            request.setCookies(cookies);
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}
