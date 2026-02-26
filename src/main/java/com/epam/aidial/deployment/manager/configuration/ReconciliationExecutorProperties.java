package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.deployment.reconcile.executor")
public class ReconciliationExecutorProperties {

    /**
     * Number of threads in the reconciliation thread pool.
     * Bounds concurrent reconciliation tasks triggered by Kubernetes informer events.
     */
    private int threadPoolSize;

    /**
     * Maximum number of pending reconcile tasks in the queue.
     * When full, the caller runs the task (backpressure) via CallerRunsPolicy.
     */
    private int queueCapacity;

    /**
     * Thread name prefix for reconciliation pool threads (e.g. for logging and thread dumps).
     */
    private String threadNamePrefix;
}
