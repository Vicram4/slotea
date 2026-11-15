package com.slotea.shared.links;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param secret clave HMAC de los enlaces de gestión de citas, en Base64 (≥ 32 bytes). Variable de
 *               entorno {@code LINK_SECRET}. Cambiarla invalida todos los enlaces enviados.
 * @param webUrl URL pública de la web, para los enlaces de los emails ({@code APP_WEB_URL}).
 */
@Validated
@ConfigurationProperties("app.links")
public record LinkProperties(
        @NotBlank(message = "LINK_SECRET is not set (see .env.example)") String secret,
        @NotNull URI webUrl) {
}
