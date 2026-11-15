package com.slotea.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.auth.AuthService;
import com.slotea.auth.AuthService.Session;
import com.slotea.demo.DemoDtos.DemoRole;
import com.slotea.demo.DemoSeeder.SeededDemo;
import com.slotea.shared.web.ApiException;

/**
 * Demo en un clic: cada visitante recibe <strong>su propia copia</strong> del negocio de demostración.
 * Lo que haga (cancelar citas, cambiar horarios…) no lo ve nadie más, y la copia se borra a las
 * {@link #TTL}.
 *
 * <p>Solo SQL con {@code business_id} explícito: no interviene el tenant de Hibernate.
 */
@Service
class DemoService {

    static final Duration TTL = Duration.ofHours(24);
    /** Tope de copias vivas (cada una son unas 500 filas; el plan gratuito de Neon da ~0,5 GB). */
    static final int MAX_LIVE_COPIES = 100;

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final DemoSeeder seeder;
    private final AuthService auth;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    DemoService(DemoSeeder seeder, AuthService auth, JdbcClient jdbc, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.seeder = seeder;
        this.auth = auth;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Crea una copia y abre sesión en ella como dueño o como empleado. */
    Session create(DemoRole role) {
        if (liveCopies() >= MAX_LIVE_COPIES) {
            // Si el cron de limpieza no ha pasado, limpiamos aquí antes de rendirnos.
            deleteExpired();
            if (liveCopies() >= MAX_LIVE_COPIES) {
                throw ApiException.serviceUnavailable("La demo está llena ahora mismo. Prueba de nuevo en un rato.");
            }
        }
        SeededDemo demo = tx.execute(_ -> seeder.seed(DemoTemplates.BARBERSHOP, clock.instant()));
        log.info("Demo copy {} created", demo.slug());
        return auth.demoSession(role == DemoRole.OWNER ? demo.ownerUserId() : demo.employeeUserId(),
                demo.businessId());
    }

    /** Pasa de dueño a empleado (o al revés) dentro de la misma copia. */
    Session switchTo(UUID businessId, DemoRole role) {
        UUID userId = tx.execute(_ -> jdbc.sql("""
                SELECT m.user_id FROM membership m JOIN business b ON b.id = m.business_id
                WHERE m.business_id = ? AND b.is_demo AND m.role = ?
                ORDER BY m.created_at, m.id LIMIT 1""")
                .params(businessId, role.name())
                .query(UUID.class)
                .optional()
                .orElse(null));
        if (userId == null) {
            throw ApiException.notFound("Solo se puede cambiar de rol en la demo.");
        }
        return auth.demoSession(userId, businessId);
    }

    /**
     * Borra las copias de más de {@link #TTL}: primero sus usuarios (solo los que no pertenecen a
     * ningún negocio real) y después el negocio, que arrastra en cascada todo lo demás.
     */
    int deleteExpired() {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(clock.instant().minus(TTL), ZoneOffset.UTC);
        Integer deleted = tx.execute(_ -> {
            jdbc.sql("""
                    DELETE FROM app_user u
                    WHERE EXISTS (SELECT 1 FROM membership m JOIN business b ON b.id = m.business_id
                                  WHERE m.user_id = u.id AND b.is_demo AND b.created_at < :cutoff)
                      AND NOT EXISTS (SELECT 1 FROM membership m JOIN business b ON b.id = m.business_id
                                      WHERE m.user_id = u.id AND NOT b.is_demo)""")
                    .param("cutoff", cutoff)
                    .update();
            return jdbc.sql("DELETE FROM business WHERE is_demo AND created_at < ?").param(cutoff).update();
        });
        int count = deleted == null ? 0 : deleted;
        if (count > 0) {
            log.info("Deleted {} expired demo copies", count);
        }
        return count;
    }

    private long liveCopies() {
        Instant cutoff = clock.instant().minus(TTL);
        Long count = tx.execute(_ -> jdbc.sql("SELECT count(*) FROM business WHERE is_demo AND created_at >= ?")
                .param(OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .query(Long.class)
                .single());
        return count == null ? 0 : count;
    }
}
