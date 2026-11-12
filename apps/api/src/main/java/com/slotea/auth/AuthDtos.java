package com.slotea.auth;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.slotea.business.MembershipSummary;
import com.slotea.shared.validation.TimeZoneId;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contrato HTTP del módulo de autenticación (los tipos de TS se generan desde aquí). */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Alta de un negocio nuevo con su dueño. */
    public record SignupRequest(
            @NotBlank @Size(max = 100) String businessName,
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "solo minúsculas, números y guiones (sin guion al principio ni al final)")
            String slug,
            @TimeZoneId @Nullable String timezone,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password) {

        @Override
        public String toString() {
            return "SignupRequest[slug=" + slug + ", email=" + email + "]";
        }
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    /** Quién soy: el usuario, el negocio activo de la sesión (si tiene) y todos sus negocios. */
    public record MeResponse(UserView user, @Nullable MembershipSummary activeBusiness,
            List<MembershipSummary> memberships) {
    }

    public record UserView(UUID id, String email, String fullName) {
    }
}
