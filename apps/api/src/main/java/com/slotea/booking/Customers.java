package com.slotea.booking;

import java.util.UUID;

import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.slotea.shared.tenancy.TenantContext;

/**
 * Fichas de cliente del negocio actual, identificadas por email.
 *
 * <p>Va con SQL para usar {@code INSERT … ON CONFLICT DO NOTHING}: si dos reservas con el mismo email
 * nuevo llegan a la vez, PostgreSQL hace esperar a la segunda y ninguna falla. Con JPA («busca y, si
 * no está, inserta») la segunda chocaría con el {@code UNIQUE}.
 *
 * <p>Los datos de un cliente que ya existe <strong>no se sobrescriben</strong>: el email no está
 * verificado y no debe servir para cambiar el nombre o el teléfono de otra persona.
 */
@Component
class Customers {

    private final JdbcClient jdbc;

    Customers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Id del cliente con ese email (ya normalizado), creándolo si no existe. */
    UUID findOrCreate(String email, String fullName, @Nullable String phone) {
        UUID businessId = TenantContext.requireBusinessId();
        jdbc.sql("""
                INSERT INTO customer (id, business_id, email, full_name, phone) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (business_id, email) DO NOTHING
                """)
                // El mismo generador UUID v7 que usan las entidades (@UuidGenerator); no usa la sesión.
                .params(UuidVersion7Strategy.INSTANCE.generateUuid(null), businessId, email, fullName, phone)
                .update();
        return jdbc.sql("SELECT id FROM customer WHERE business_id = ? AND email = ?")
                .params(businessId, email)
                .query(UUID.class)
                .single();
    }
}
