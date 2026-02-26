package com.epam.aidial.deployment.manager.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ExecutorServiceConfiguration {

    @Bean(name = "k8s-service-readiness-checker", destroyMethod = "shutdown")
    public ExecutorService k8sServiceReadinessCheckerExecutor(ReconciliationExecutorProperties properties) {
        var queue = new LinkedBlockingQueue<Runnable>(properties.getQueueCapacity());
        var threadFactory = Thread.ofPlatform()
                .name(properties.getThreadNamePrefix() + "-", 1)
                .factory();

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
}
