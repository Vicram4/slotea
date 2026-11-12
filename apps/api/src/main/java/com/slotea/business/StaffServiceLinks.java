package com.slotea.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.slotea.shared.tenancy.TenantContext;

/**
 * Qué servicios hace cada profesional (tabla {@code staff_service}).
 *
 * <p>Es una tabla puente con clave {@code (business_id, staff_id, service_id)}: {@code @TenantId} no
 * se aplica a las tablas de un {@code @ManyToMany}, así que la manejamos con SQL y el
 * {@code business_id} se pone siempre desde {@link TenantContext}, nunca desde la petición. Además,
 * las dos FKs compuestas impiden por esquema mezclar un profesional y un servicio de negocios distintos.
 */
@Component
class StaffServiceLinks {

    private final JdbcClient jdbc;

    StaffServiceLinks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Servicios de cada profesional del negocio actual. */
    Map<UUID, List<UUID>> serviceIdsByStaff() {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT ss.staff_id, ss.service_id FROM staff_service ss
                JOIN service s ON s.business_id = ss.business_id AND s.id = ss.service_id
                WHERE ss.business_id = ? ORDER BY lower(s.name), s.id
                """)
                .param(TenantContext.requireBusinessId())
                .query((rs, _) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), _ -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    List<UUID> serviceIds(UUID staffId) {
        return jdbc.sql("""
                SELECT ss.service_id FROM staff_service ss
                JOIN service s ON s.business_id = ss.business_id AND s.id = ss.service_id
                WHERE ss.business_id = ? AND ss.staff_id = ? ORDER BY lower(s.name), s.id
                """)
                .params(TenantContext.requireBusinessId(), staffId)
                .query(UUID.class)
                .list();
    }

    /** Sustituye el conjunto entero (es lo que edita la pantalla: una lista de casillas). */
    void replace(UUID staffId, Collection<UUID> serviceIds) {
        UUID businessId = TenantContext.requireBusinessId();
        jdbc.sql("DELETE FROM staff_service WHERE business_id = ? AND staff_id = ?")
                .params(businessId, staffId)
                .update();
        for (UUID serviceId : serviceIds) {
            jdbc.sql("INSERT INTO staff_service (business_id, staff_id, service_id) VALUES (?, ?, ?)")
                    .params(businessId, staffId, serviceId)
                    .update();
        }
    }
}
