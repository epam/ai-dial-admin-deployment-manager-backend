package com.epam.aidial.deployment.manager.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

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
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1_000);
        backOffPolicy.setMaxInterval(10_000);
        backOffPolicy.setMultiplier(1.5);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        TimeoutRetryPolicy retryPolicy = new TimeoutRetryPolicy();
        retryPolicy.setTimeout(timeout.toMillis());
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}

