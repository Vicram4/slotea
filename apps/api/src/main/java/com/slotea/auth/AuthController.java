package com.slotea.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.auth.AuthDtos.LoginRequest;
import com.slotea.auth.AuthDtos.MeResponse;
import com.slotea.auth.AuthDtos.SignupRequest;
import com.slotea.shared.security.AuthClaims;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Los tokens viajan solo en cookies {@code httpOnly}; el cuerpo de la respuesta es el «quién soy». */
@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService auth;
    private final AuthCookies cookies;

    AuthController(AuthService auth, AuthCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    /**
     * Genera la cookie {@code XSRF-TOKEN}. La SPA la llama antes del primer POST y después devuelve su
     * valor en la cabecera {@code X-XSRF-TOKEN}.
     */
    @Operation(summary = "Obtener la cookie CSRF")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void csrf(@Parameter(hidden = true) CsrfToken token) {
        token.getToken(); // cargar el token diferido obliga a escribir la cookie
    }

    @Operation(summary = "Registrar un negocio y su dueño (inicia sesión)")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    MeResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        return open(auth.signup(request), response);
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return open(auth.login(request.email(), request.password()), response);
    }

    @Operation(summary = "Renovar la sesión (rota el refresh token)")
    @PostMapping("/refresh")
    MeResponse refresh(@Parameter(hidden = true) @CookieValue(name = AuthClaims.REFRESH_COOKIE, required = false)
            @Nullable String refreshToken,
            HttpServletResponse response) {
        try {
            return open(auth.refresh(refreshToken), response);
        } catch (RuntimeException e) {
            cookies.clear(response);
            throw e;
        }
    }

    @Operation(summary = "Cerrar sesión (revoca el refresh token)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Parameter(hidden = true) @CookieValue(name = AuthClaims.REFRESH_COOKIE, required = false)
            @Nullable String refreshToken,
            HttpServletResponse response) {
        auth.logout(refreshToken);
        cookies.clear(response);
    }

    private MeResponse open(AuthService.Session session, HttpServletResponse response) {
        cookies.write(response, session.tokens());
        return session.me();
    }
}
