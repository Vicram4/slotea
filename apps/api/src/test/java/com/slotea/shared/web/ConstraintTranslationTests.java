package com.slotea.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.TestcontainersConfiguration;
import com.slotea.schedule.WorkingHours;
import com.slotea.schedule.WorkingHoursRepository;
import com.slotea.shared.tenancy.TenantContext;

/**
 * Hibernate 7 no traduce las violaciones de {@code EXCLUDE} (SQLState 23P01) a una
 * {@code ConstraintViolationException} con nombre. Este test comprueba con PostgreSQL real que, aun
 * así, llegan como {@link DataAccessException} y que {@link ApiExceptionHandler} encuentra el nombre
 * de la restricción (si dos peticiones compiten, la respuesta es un 409 y no un 500).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConstraintTranslationTests {

    @Autowired
    WorkingHoursRepository hours;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void exclusionViolationsThroughJpaKeepTheirConstraintName() {
        UUID business = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        jdbc.sql("INSERT INTO business (id, slug, name, contact_email) VALUES (?, ?, 'T', 'x@example.test')")
                .params(business, "x-" + business.toString().substring(0, 8)).update();
        jdbc.sql("INSERT INTO staff_member (id, business_id, display_name) VALUES (?, ?, 'Ana')")
                .params(staff, business).update();

        Throwable thrown = catchThrowable(() -> TenantContext.callAs(business,
                () -> new TransactionTemplate(transactionManager).execute(_ -> hours.saveAllAndFlush(java.util.List.of(
                        new WorkingHours(staff, 1, LocalTime.of(9, 0), LocalTime.of(14, 0)),
                        new WorkingHours(staff, 1, LocalTime.of(13, 0), LocalTime.of(18, 0)))))));

        assertThat(thrown).isInstanceOf(DataAccessException.class);
        assertThat(ApiExceptionHandler.violatedConstraint(thrown)).isEqualTo("working_hours_no_overlap");
    }
}
