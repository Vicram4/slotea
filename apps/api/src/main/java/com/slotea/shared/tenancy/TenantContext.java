package com.slotea.shared.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Negocio (tenant) de la petición en curso.
 *
 * <p>Usa un {@link ScopedValue} (Java 25) en lugar de un {@code ThreadLocal}: el valor solo existe
 * dentro del bloque que lo fija, no se puede "olvidar" limpiarlo y funciona con hilos virtuales.
 *
 * <p>Si no hay negocio, Hibernate recibe {@link #NO_TENANT}, un id que no existe: una consulta sin
 * tenant devuelve vacío en vez de los datos de todos (<em>fail closed</em>). Las pocas operaciones
 * que necesitan cruzar negocios (login, registro, «mis negocios») usan {@link SystemTransactions}.
 */
public final class TenantContext {

    /** Valor por defecto: no coincide con ningún negocio. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    /** Tenant «raíz»: Hibernate no filtra. Solo se fija a través de {@link SystemTransactions}. */
    static final UUID SYSTEM = new UUID(-1L, -1L);

    private static final ScopedValue<UUID> CURRENT = ScopedValue.newInstance();

    private TenantContext() {
    }

    /** Negocio de la petición, si lo hay (nunca devuelve los valores especiales). */
    public static Optional<UUID> currentBusinessId() {
        return CURRENT.isBound() && !CURRENT.get().equals(SYSTEM) ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    /** Negocio de la petición; si no hay, es un error de programación (el endpoint debería exigir rol). */
    public static UUID requireBusinessId() {
        return currentBusinessId().orElseThrow(() -> new IllegalStateException("No tenant bound to this request"));
    }

    /** Ejecuta {@code op} con {@code businessId} como tenant. */
    public static <T, X extends Throwable> T callAs(UUID businessId, ScopedValue.CallableOp<T, X> op) throws X {
        if (businessId.equals(SYSTEM) || businessId.equals(NO_TENANT)) {
            throw new IllegalArgumentException("Reserved tenant id");
        }
        return ScopedValue.where(CURRENT, businessId).call(op);
    }

    static <T, X extends Throwable> T callAsSystem(ScopedValue.CallableOp<T, X> op) throws X {
        return ScopedValue.where(CURRENT, SYSTEM).call(op);
    }

    /** Lo que ve Hibernate al abrir una sesión. */
    static UUID resolve() {
        return CURRENT.orElse(NO_TENANT);
    }
}
