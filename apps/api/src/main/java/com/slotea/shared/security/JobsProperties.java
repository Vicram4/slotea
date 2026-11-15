package com.slotea.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Pattern;

/**
 * @param token secreto compartido con el cron que llama a {@code /internal/jobs/*} ({@code JOBS_TOKEN}).
 *              Al menos 32 caracteres. Sin él, esos endpoints responden siempre 401.
 */
@Validated
@ConfigurationProperties("app.jobs")
public record JobsProperties(
        @Pattern(regexp = "^$|^.{32,}$", message = "JOBS_TOKEN must have at least 32 characters") String token) {

    public JobsProperties {
        token = token == null ? "" : token.strip();
    }

    boolean enabled() {
        return !token.isEmpty();
    }
}
