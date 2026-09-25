package com.shivankkapoor.aldrop.Cache;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "aldrop.cache.backend", havingValue = "caffeine", matchIfMissing = true)
public class CacheConfig {

    @Bean
    public DataCache<String, CachedPlatform> platformCache(
            @Value("${aldrop.cache.platform-ttl:30s}") Duration ttl,
            @Value("${aldrop.cache.platform-max-size:1000}") long maxSize) {
        return new CaffeineDataCache<>(ttl, maxSize);
    }

    @Bean
    public DataCache<UUID, CachedUser> userActiveCache(
            @Value("${aldrop.cache.user-active-ttl:10s}") Duration ttl,
            @Value("${aldrop.cache.user-active-max-size:100000}") long maxSize) {
        return new CaffeineDataCache<>(ttl, maxSize);
    }

    @Bean
    public DataCache<String, CachedSession> sessionCache(
            @Value("${aldrop.cache.session-ttl:10s}") Duration ttl,
            @Value("${aldrop.cache.session-max-size:100000}") long maxSize) {
        return new CaffeineDataCache<>(ttl, maxSize);
    }
}
