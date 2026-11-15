package com.slotea.demo;

import jakarta.validation.constraints.NotNull;

/** Contrato HTTP de la demo (los tipos de TS se generan desde aquí). */
public final class DemoDtos {

    private DemoDtos() {
    }

    /** Con qué cuenta de la copia se entra. El «cliente» no tiene cuenta: es la página pública. */
    public enum DemoRole { OWNER, STAFF }

    public record DemoSessionRequest(@NotNull DemoRole role) {
    }
}
