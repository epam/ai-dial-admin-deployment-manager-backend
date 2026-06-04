package com.epam.aidial.deployment.manager.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;
import java.util.function.Function;

@Configuration
public class RetryTemplateConfiguration {

    @Bean
    @Qualifier("mcpHealthCheckerRetryTemplateFactory")
    public Function<Duration, RetryTemplate> mcpHealthCheckerRetryTemplateFactory() {
        return this::createRetryTemplate;
    }

    private RetryTemplate createRetryTemplate(Duration timeout) {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                // attempts are capped by the overall timeout only, mirroring the former spring-retry TimeoutRetryPolicy
                .maxRetries(Long.MAX_VALUE)
                .delay(Duration.ofSeconds(1))
                .maxDelay(Duration.ofSeconds(10))
                .multiplier(1.5)
                .timeout(timeout)
                .build();

        return new RetryTemplate(retryPolicy);
    }
}
