package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.metrics.scrape")
public class MetricsScrapeProperties {
    private boolean enabled;
    private long timeoutMs;
    private long cacheTtlMs;
    private ResourceUsage resourceUsage;

    @Data
    public static class ResourceUsage {
        private boolean enabled;
    }
}
