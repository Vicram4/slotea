package com.slotea.shared.security;

/** Contrato del JWT de acceso (lo emite {@code auth}, lo leen la seguridad y la tenancy). */
public final class AuthClaims {

    public static final String ISSUER = "slotea-api";
    /** Negocio activo de la sesión (tenant). */
    public static final String BUSINESS_ID = "bid";
    /** Rol en el negocio activo: OWNER o STAFF. */
    public static final String ROLE = "role";

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    /** La cookie de refresh solo viaja a los endpoints de auth. */
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private AuthClaims() {
    }
}
