package com.epam.aidial.deployment.manager.transaction.timestamp;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TransactionTimestampAspect {

    @Before("@annotation(jakarta.transaction.Transactional) || @within(jakarta.transaction.Transactional)")
    public void bindTimestamp() {
        bindIfAbsent();
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public void bindTimestampSpring() {
        bindIfAbsent();
    }

    private void bindIfAbsent() {
        if (TransactionSynchronizationManager.hasResource(TransactionTimestampContext.TRANSACTION_TIMESTAMP_KEY)) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        TransactionSynchronizationManager.bindResource(
                TransactionTimestampContext.TRANSACTION_TIMESTAMP_KEY, timestamp);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(
                        TransactionTimestampContext.TRANSACTION_TIMESTAMP_KEY);
            }
        });
    }
}
