package com.slotea.auth;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.auth.AuthDtos.MeResponse;
import com.slotea.shared.security.AuthClaims;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "auth")
@RestController
class MeController {

    private final AuthService auth;

    MeController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "Quién soy: usuario, negocio activo y roles")
    @GetMapping("/api/v1/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        String businessId = jwt.getClaimAsString(AuthClaims.BUSINESS_ID);
        return auth.me(UUID.fromString(jwt.getSubject()), businessId != null ? UUID.fromString(businessId) : null);
    }
}
