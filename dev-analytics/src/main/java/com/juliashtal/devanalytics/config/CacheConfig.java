package com.juliashtal.devanalytics.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Configures the Caffeine caches, including the ai_summaries cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                caffeineCache("ai_summaries",             6, TimeUnit.HOURS,   500),
                caffeineCache("github-discover-repos",   60, TimeUnit.SECONDS, 200),
                caffeineCache("jira-discover-projects",  60, TimeUnit.SECONDS, 200)
        ));
        return manager;
    }

    private static CaffeineCache caffeineCache(String name, long duration, TimeUnit unit, long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(maxSize)
                .build());
    }
}
