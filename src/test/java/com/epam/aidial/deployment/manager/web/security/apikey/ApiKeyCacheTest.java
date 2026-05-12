package com.epam.aidial.deployment.manager.web.security.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCacheTest {

    @Test
    void shouldCallLoaderOnceForSameKey() {
        ApiKeyCache cache = newCache(60, 100);
        AtomicInteger calls = new AtomicInteger();
        Authentication first = cache.getOrAuthenticate("k1", () -> {
            calls.incrementAndGet();
            return token("p1");
        });
        Authentication second = cache.getOrAuthenticate("k1", () -> {
            calls.incrementAndGet();
            return token("p1");
        });

        assertThat(first).isSameAs(second);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldCallLoaderPerDistinctKey() {
        ApiKeyCache cache = newCache(60, 100);
        AtomicInteger calls = new AtomicInteger();
        cache.getOrAuthenticate("k1", () -> {
            calls.incrementAndGet();
            return token("p1");
        });
        cache.getOrAuthenticate("k2", () -> {
            calls.incrementAndGet();
            return token("p2");
        });
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void shouldNotCacheFailures() {
        ApiKeyCache cache = newCache(60, 100);
        try {
            cache.getOrAuthenticate("k1", () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {
            // expected
        }
        AtomicInteger calls = new AtomicInteger();
        cache.getOrAuthenticate("k1", () -> {
            calls.incrementAndGet();
            return token("p1");
        });
        assertThat(calls.get()).isEqualTo(1);
    }

    private static ApiKeyCache newCache(int ttlSeconds, int maxSize) {
        ApiKeyProperties properties = new ApiKeyProperties(new ObjectMapper());
        properties.setCacheTtlSeconds(ttlSeconds);
        properties.setCacheMaxSize(maxSize);
        return new ApiKeyCache(properties);
    }

    private static Authentication token(String project) {
        return new ApiKeyAuthenticationToken(project, List.of());
    }
}
