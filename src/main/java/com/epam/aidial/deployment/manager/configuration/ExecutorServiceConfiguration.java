package com.epam.aidial.deployment.manager.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ExecutorServiceConfiguration {

    /**
     * Upper bound on concurrent in-flight metric scrapes. The pod-proxy read is blocking and not
     * interruptible, so the per-scrape timeout bounds only the wait — abandoned reads keep running
     * until the underlying HTTP client gives up. Capping them on a dedicated daemon pool keeps that
     * backlog bounded and off the shared {@link java.util.concurrent.ForkJoinPool#commonPool()}.
     */
    private static final int METRICS_SCRAPE_POOL_SIZE = 8;

    @Bean(name = "metrics-scrape", destroyMethod = "shutdown")
    public ExecutorService metricsScrapeExecutor() {
        var threadFactory = Thread.ofPlatform()
                .daemon(true)
                .name("metrics-scrape-", 1)
                .factory();
        return Executors.newFixedThreadPool(METRICS_SCRAPE_POOL_SIZE, threadFactory);
    }

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
