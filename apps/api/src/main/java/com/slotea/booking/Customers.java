package com.slotea.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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

    /** Cómo localizar a un cliente. */
    record Contact(String email, String fullName, @Nullable String phone) {

        @Override
        public String toString() {
            return "Contact[***]";
        }
    }

    /** Datos de contacto de un cliente del negocio actual. */
    Contact contact(UUID customerId) {
        return jdbc.sql("SELECT email, full_name, phone FROM customer WHERE business_id = ? AND id = ?")
                .params(TenantContext.requireBusinessId(), customerId)
                .query((rs, _) -> contact(rs))
                .single();
    }

    /** Los de varios clientes de una vez (la agenda), por id. */
    Map<UUID, Contact> contacts(Collection<UUID> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Contact> found = new HashMap<>();
        jdbc.sql("SELECT id, email, full_name, phone FROM customer WHERE business_id = ? AND id = ANY(?)")
                .params(TenantContext.requireBusinessId(), customerIds.toArray(UUID[]::new))
                .query(rs -> {
                    found.put(rs.getObject("id", UUID.class), contact(rs));
                });
        return found;
    }

    private static Contact contact(ResultSet rs) throws SQLException {
        return new Contact(rs.getString("email"), rs.getString("full_name"), rs.getString("phone"));
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
