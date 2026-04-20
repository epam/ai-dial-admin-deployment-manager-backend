package com.epam.aidial.deployment.manager.configuration.datasource;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditEntityPackage;
import com.epam.aidial.deployment.manager.dao.entity.JpaEntityPackage;
import com.epam.aidial.deployment.manager.dao.jpa.JpaPackage;
import com.epam.aidial.deployment.manager.transaction.timestamp.TransactionTimestampContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Instant;
import java.util.Optional;

@Configuration("mcpJpaConfiguration")
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 10, proxyTargetClass = true)
@EnableJpaRepositories(basePackageClasses = {
        JpaPackage.class,
})
@EntityScan(basePackageClasses = {
        JpaEntityPackage.class,
        AuditEntityPackage.class,
})
@EnableJpaAuditing(dateTimeProviderRef = "transactionDateTimeProvider")
@Slf4j
public class JpaConfiguration {

    @Bean
    DateTimeProvider transactionDateTimeProvider(ObjectProvider<TransactionTimestampContext> contextProvider) {
        return () -> {
            TransactionTimestampContext ctx = contextProvider.getIfAvailable();
            if (ctx != null) {
                try {
                    return Optional.of(Instant.ofEpochMilli(ctx.getTimestamp()));
                } catch (IllegalStateException e) {
                    log.warn("Transaction timestamp not available, falling back to Instant.now()", e);
                    return Optional.of(Instant.now());
                }
            }
            log.warn("TransactionTimestampContext not available, falling back to Instant.now()");
            return Optional.of(Instant.now());
        };
    }
}
