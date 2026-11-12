package com.slotea.shared.tenancy;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Único punto de entrada para operaciones que cruzan negocios (login, registro, «mis negocios»).
 *
 * <p>Abre una transacción nueva con el tenant raíz. Falla si ya hay una transacción abierta:
 * Hibernate ya habría fijado el tenant de esa sesión y el cambio no tendría efecto.
 */
@Component
public class SystemTransactions {

    private final TransactionTemplate tx;

    SystemTransactions(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("System transactions must not run inside another transaction");
        }
        return TenantContext.callAsSystem(() -> tx.execute(_ -> work.get()));
    }
}
