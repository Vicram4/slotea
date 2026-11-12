package com.slotea.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.TestcontainersConfiguration;
import com.slotea.auth.AppUser;
import com.slotea.auth.AppUserRepository;
import com.slotea.business.Membership;
import com.slotea.business.MembershipRepository;
import com.slotea.business.Role;

/**
 * La capa de aplicación del aislamiento ({@code @TenantId}) probada directamente sobre los
 * repositorios, sin HTTP: si alguien se salta el filtro, estos tests lo detectan.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenancyTests {

    @Autowired
    MembershipRepository memberships;

    @Autowired
    AppUserRepository users;

    @Autowired
    SystemTransactions system;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    UUID businessA;
    UUID businessB;
    UUID userA;
    UUID userB;

    @BeforeEach
    void twoBusinessesWithOneMemberEach() {
        businessA = insertBusiness();
        businessB = insertBusiness();
        userA = insertMember(businessA);
        userB = insertMember(businessB);
    }

    @Test
    void withoutTenantNothingIsVisible() {
        // Fail closed: un olvido no expone los datos de todos los negocios.
        assertThat(memberships.findAll()).isEmpty();
        assertThat(memberships.findByUserId(userA)).isEmpty();
    }

    @Test
    void withTenantOnlyThatBusinessIsVisible() {
        var visible = TenantContext.callAs(businessA, () -> memberships.findAllWithUser());
        assertThat(visible).extracting(Membership::getBusinessId).containsOnly(businessA);
        assertThat(visible).extracting(m -> m.getUser().getId()).contains(userA).doesNotContain(userB);

        assertThat(TenantContext.callAs(businessA, () -> memberships.findByUserId(userB))).isEmpty();
    }

    @Test
    void insertsAreStampedWithTheCurrentTenant() {
        UUID newUser = TenantContext.callAs(businessA, () -> {
            var tx = new TransactionTemplate(transactionManager);
            return tx.execute(_ -> {
                var user = users.save(new AppUser(UUID.randomUUID() + "@example.test", "{noop}x", "Nuevo",
                        Instant.now()));
                memberships.save(new Membership(user, Role.STAFF, Instant.now()));
                return user.getId();
            });
        });
        UUID stored = jdbc.sql("SELECT business_id FROM membership WHERE user_id = ?")
                .param(newUser).query(UUID.class).single();
        assertThat(stored).isEqualTo(businessA);
    }

    @Test
    void aTenantCannotInsertIntoAnotherBusiness() {
        assertThatThrownBy(() -> TenantContext.callAs(businessA, () -> {
            var tx = new TransactionTemplate(transactionManager);
            return tx.execute(_ -> {
                var user = users.save(new AppUser(UUID.randomUUID() + "@example.test", "{noop}x", "Intruso",
                        Instant.now()));
                return memberships.saveAndFlush(Membership.forBusiness(businessB, user, Role.OWNER, Instant.now()));
            });
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("tenant");
        long intruders = jdbc.sql("SELECT count(*) FROM membership m JOIN app_user u ON u.id = m.user_id "
                + "WHERE m.business_id = ? AND u.full_name = 'Intruso'").param(businessB).query(Long.class).single();
        assertThat(intruders).isZero();
    }

    @Test
    void systemTransactionsSeeEveryBusiness() {
        var all = system.execute(() -> memberships.findAll());
        assertThat(all).extracting(Membership::getBusinessId).contains(businessA, businessB);
    }

    @Test
    void systemTransactionsRefuseToRunInsideAnotherTransaction() {
        var tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(_ -> system.execute(() -> memberships.findAll())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reservedTenantIdsCannotBeBoundDirectly() {
        assertThatThrownBy(() -> TenantContext.callAs(TenantContext.SYSTEM, () -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantContext.callAs(TenantContext.NO_TENANT, () -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID insertBusiness() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO business (id, slug, name, contact_email) VALUES (?, ?, 'Test', 'x@example.test')")
                .params(id, "t-" + id.toString().substring(0, 8))
                .update();
        return id;
    }

    private UUID insertMember(UUID businessId) {
        UUID userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO app_user (id, email, password_hash, full_name) VALUES (?, ?, '{noop}x', 'Miembro')")
                .params(userId, userId + "@example.test")
                .update();
        jdbc.sql("INSERT INTO membership (id, business_id, user_id, role) VALUES (?, ?, ?, 'OWNER')")
                .params(UUID.randomUUID(), businessId, userId)
                .update();
        return userId;
    }
}
