package com.slotea.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * @param jwtSecret       clave HS256 en Base64 (≥ 32 bytes). Variable de entorno {@code JWT_SECRET}.
 * @param accessTokenTtl  vida del JWT de acceso (y de su cookie).
 * @param refreshTokenTtl vida de cada refresh token (cada uso lo rota).
 * @param cookieSecure    atributo {@code Secure} de las cookies (solo se desactiva en tests).
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank(message = "JWT_SECRET is not set (see .env.example)") String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("true") boolean cookieSecure) {
}
