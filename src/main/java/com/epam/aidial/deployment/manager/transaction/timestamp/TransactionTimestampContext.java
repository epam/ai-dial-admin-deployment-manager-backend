package com.epam.aidial.deployment.manager.transaction.timestamp;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionTimestampContext {

    static final String TRANSACTION_TIMESTAMP_KEY = "TRANSACTION_TIMESTAMP";

    public long getTimestamp() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("No active transaction");
        }
        Long timestamp = (Long) TransactionSynchronizationManager.getResource(TRANSACTION_TIMESTAMP_KEY);
        if (timestamp == null) {
            throw new IllegalStateException("Transaction timestamp not initialized");
        }
        return timestamp;
    }
}
