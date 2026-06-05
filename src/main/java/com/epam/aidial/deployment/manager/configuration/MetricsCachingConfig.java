package com.epam.aidial.deployment.manager.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Registers the short-TTL response cache for deployment metrics snapshots on the application
 * {@link CacheManager} (defined in {@code HuggingFaceCachingConfig}). The cache bounds
 * API-server load under rapid repeated requests (e.g. an auto-refreshing UI panel).
 */
@Configuration
@RequiredArgsConstructor
public class MetricsCachingConfig implements InitializingBean {

    public static final String DEPLOYMENT_METRICS_CACHE_NAME = "DeploymentMetricsCache";

    private final CacheManager cacheManager;
    private final MetricsScrapeProperties properties;

    @Override
    public void afterPropertiesSet() {
        if (cacheManager instanceof CaffeineCacheManager caffeineCacheManager) {
            caffeineCacheManager.registerCustomCache(DEPLOYMENT_METRICS_CACHE_NAME,
                    Caffeine.newBuilder()
                            .expireAfterWrite(Duration.ofMillis(properties.getCacheTtlMs()))
                            .build());
        }
    }

}
