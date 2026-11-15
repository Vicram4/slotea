package com.slotea.demo;

import jakarta.validation.constraints.NotNull;

/** Contrato HTTP de la demo (los tipos de TS se generan desde aquí). */
public final class DemoDtos {

    private DemoDtos() {
    }

    /** Con qué cuenta de la copia se entra. El «cliente» no tiene cuenta: es la página pública. */
    public enum DemoRole { OWNER, STAFF }

    /** De qué sector es la copia. El contenido de cada uno está en {@link DemoTemplates}. */
    public enum DemoSector { BARBERSHOP, HAIR_AND_BEAUTY, MECHANIC, PSYCHOLOGY, PHYSIOTHERAPY, VETERINARY }

    /** {@code STAFF} no vale en un sector sin empleado con cuenta (psicología): 400. */
    public record DemoSessionRequest(@NotNull DemoSector sector, @NotNull DemoRole role) {
    }

    public record DemoSwitchRequest(@NotNull DemoRole role) {
    }
}
