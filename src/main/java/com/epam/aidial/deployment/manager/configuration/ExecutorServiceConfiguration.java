package com.epam.aidial.deployment.manager.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorServiceConfiguration {

    @Bean("k8s-service-readiness-checker")
    public ExecutorService componentCleanerExecutor() {
        return Executors.newCachedThreadPool(r -> new Thread(r, "k8s-service-readiness-checker-thread-pool"));
    }

}
