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

    @Positive(message = "app.metrics.scrape.timeout-ms must be positive")
    private long timeoutMs;

    @Positive(message = "app.metrics.scrape.cache-ttl-ms must be positive")
    private long cacheTtlMs;

    private ResourceUsage resourceUsage;

    private Gpu gpu;

    @Data
    public static class ResourceUsage {
        private boolean enabled;
    }

    /**
     * GPU telemetry via the NVIDIA DCGM exporter. When enabled, per-pod GPU metrics are read for
     * deployments that request {@code nvidia.com/gpu}, by scraping the co-located dcgm-exporter pods'
     * Prometheus endpoint through the API-server pod proxy. Fields carry no Java initializers — the
     * defaults live in {@code application.yml} (constitution: configuration property defaults).
     */
    @Data
    public static class Gpu {
        private boolean enabled;
        private String namespace;
        private String podLabelSelector;

        @Positive(message = "app.metrics.scrape.gpu.port must be positive")
        private int port;

        private String metricsPath;
        private String podLabel;
        private String namespaceLabel;
    }
}
