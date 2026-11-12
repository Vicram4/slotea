package com.slotea.shared.tenancy;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Conecta {@link TenantContext} con Hibernate. En las entidades con {@code @TenantId}, Hibernate
 * añade {@code business_id = ?} a cada consulta y rellena la columna al insertar.
 *
 * <p>El tenant se lee <strong>al abrir la sesión</strong> (al empezar la transacción): cambiar el
 * contexto a mitad de una transacción no tiene efecto. Por eso {@link SystemTransactions} exige que
 * no haya ninguna transacción abierta.
 */
@Component
class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.resolve();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    /** El tenant raíz desactiva el filtro (y permite fijar business_id a mano al insertar). */
    @Override
    public boolean isRoot(UUID tenantId) {
        return TenantContext.SYSTEM.equals(tenantId);
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
