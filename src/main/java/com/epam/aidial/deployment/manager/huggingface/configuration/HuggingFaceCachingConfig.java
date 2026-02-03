package com.epam.aidial.deployment.manager.huggingface.configuration;

import com.epam.aidial.deployment.manager.huggingface.properties.HuggingFaceProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class HuggingFaceCachingConfig {

    public static final String HF_TAG_CACHE_NAME = "HuggingFaceTagCache";

    @Bean
    public CacheManager cacheManager(HuggingFaceProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.registerCustomCache(HF_TAG_CACHE_NAME,
                Caffeine.newBuilder()
                        .expireAfterWrite(properties.getTagCacheDuration())
                        .build());

        return cacheManager;
    }

}
