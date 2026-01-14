package com.epam.aidial.deployment.manager.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class RunnerConfiguration {

    @Bean("pipeline-runner")
    public ExecutorService pipelineRunnerExecutor() {
        return Executors.newCachedThreadPool(r -> new Thread(r, "pipeline-runner-thread-pool"));
    }

    @Bean("sse-streamer")
    public ExecutorService sseStreamerExecutor() {
        return Executors.newCachedThreadPool(r -> new Thread(r, "sse-streamer-thread-pool"));
    }

}
