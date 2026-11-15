package com.slotea.shared.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién hace la petición del panel: su usuario y si es el dueño del negocio activo. Para las reglas
 * que no caben en {@code @PreAuthorize}, como «un empleado solo ve su agenda».
 */
public record Viewer(UUID userId, boolean owner) {

    public static Viewer of(Jwt jwt) {
        return new Viewer(UUID.fromString(jwt.getSubject()), "OWNER".equals(jwt.getClaimAsString(AuthClaims.ROLE)));
    }
}
