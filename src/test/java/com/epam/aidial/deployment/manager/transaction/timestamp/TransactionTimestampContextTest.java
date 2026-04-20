package com.epam.aidial.deployment.manager.transaction.timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTimestampContextTest {

    private final TransactionTimestampContext context = new TransactionTimestampContext();

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.unbindResourceIfPossible(TransactionTimestampContext.TRANSACTION_TIMESTAMP_KEY);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void getTimestamp_returnsTimestamp_whenBound() {
        long expected = 1700000000000L;
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(TransactionTimestampContext.TRANSACTION_TIMESTAMP_KEY, expected);

        assertThat(context.getTimestamp()).isEqualTo(expected);
    }

    @Test
    void getTimestamp_throwsIllegalState_whenNoActiveTransaction() {
        // No synchronization initialized and actualTransactionActive is false
        assertThatThrownBy(context::getTimestamp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No active transaction");
    }

    @Test
    void getTimestamp_throwsIllegalState_whenTimestampNotBound() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(context::getTimestamp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Transaction timestamp not initialized");
    }
}
