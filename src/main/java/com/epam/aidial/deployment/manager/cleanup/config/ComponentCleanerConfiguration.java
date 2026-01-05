package com.epam.aidial.deployment.manager.cleanup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ComponentCleanerConfiguration {

    @Bean("component-cleaner")
    public ExecutorService componentCleanerExecutor() {
        return Executors.newCachedThreadPool(r -> new Thread(r, "component-cleaner-thread-pool"));
    }

}
