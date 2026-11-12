package com.slotea.auth;

/** Par de tokens recién emitidos. Solo viajan en cookies, nunca en el cuerpo de la respuesta. */
record AuthTokens(String accessToken, String refreshToken) {

    @Override
    public String toString() {
        return "AuthTokens[***]";
    }
}
