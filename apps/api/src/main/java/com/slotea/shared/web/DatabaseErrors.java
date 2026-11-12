package com.slotea.shared.web;

import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Nombre de la restricción de PostgreSQL que ha fallado, leído del propio driver.
 *
 * <p>Hibernate 7 no da nombre a las violaciones de {@code EXCLUDE} (SQLState 23P01): llegan como un
 * error JDBC genérico. El nombre sí viene en la {@link PSQLException} de la causa.
 */
public final class DatabaseErrors {

    private DatabaseErrors() {
    }

    public static @Nullable String violatedConstraint(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql) {
                ServerErrorMessage server = psql.getServerErrorMessage();
                return server == null ? null : server.getConstraint();
            }
        }
        return null;
    }

    /** SQLState de PostgreSQL (p. ej. {@code 23P01}, {@code 40P01}), si el error viene de la BD. */
    public static @Nullable String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql) {
                return psql.getSQLState();
            }
        }
        return null;
    }
}
