package com.epam.aidial.deployment.manager.configuration;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.metrics.scrape")
public class MetricsScrapeProperties {
    private boolean enabled;

    /** Per-scrape wait bound; must be positive so {@code CompletableFuture.get(timeout)} is well-defined. */
    @Positive(message = "app.metrics.scrape.timeout-ms must be positive")
    private long timeoutMs;

    /** Response-cache TTL; must be positive — a non-positive value would disable the cache or fail Caffeine at startup. */
    @Positive(message = "app.metrics.scrape.cache-ttl-ms must be positive")
    private long cacheTtlMs;

    private ResourceUsage resourceUsage;

    @Data
    public static class ResourceUsage {
        private boolean enabled;
    }
}
