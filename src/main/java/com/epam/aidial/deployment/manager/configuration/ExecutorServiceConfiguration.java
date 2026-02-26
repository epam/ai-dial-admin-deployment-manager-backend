package com.epam.aidial.deployment.manager.configuration;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ExecutorServiceConfiguration {

    @Bean(name = "k8s-service-readiness-checker", destroyMethod = "shutdown")
    public ExecutorService k8sServiceReadinessCheckerExecutor(ReconciliationExecutorProperties properties) {
        var queue = new LinkedBlockingQueue<Runnable>(properties.getQueueCapacity());
        var threadFactory = new ThreadFactoryWithPrefix(properties.getThreadNamePrefix());
        return new ThreadPoolExecutor(
                properties.getThreadPoolSize(),
                properties.getThreadPoolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                queue,
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private static final class ThreadFactoryWithPrefix implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(0);

        ThreadFactoryWithPrefix(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, prefix + "-" + counter.incrementAndGet());
        }
    }
}
