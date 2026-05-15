package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
@LogExecution
@ConditionalOnProperty(value = "config.rest.security.api-key.enabled", havingValue = "true")
public class ApiKeyCache {

    private final Cache<String, Authentication> cache;

    public ApiKeyCache(ApiKeyProperties properties) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.getCacheTtlSeconds()))
                .maximumSize(properties.getCacheMaxSize())
                .build();
        log.debug("Initialized api-key cache: ttl={}s, maxSize={}",
                properties.getCacheTtlSeconds(), properties.getCacheMaxSize());
    }

    public Authentication getOrAuthenticate(String apiKey, Supplier<Authentication> loader) {
        String cacheKey = DigestUtils.sha256Hex(apiKey);
        return cache.get(cacheKey, k -> loader.get());
    }

    public long size() {
        return cache.estimatedSize();
    }
}
