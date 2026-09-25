package com.shivankkapoor.aldrop.Cache;

import java.time.Duration;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

public class CaffeineDataCache<K, V> implements DataCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(CaffeineDataCache.class);

    private final String name;
    private final Cache<K, V> cache;

    public CaffeineDataCache(String name, Duration ttl, long maxSize) {
        this(name, ttl, maxSize, Ticker.systemTicker());
    }

    CaffeineDataCache(String name, Duration ttl, long maxSize, Ticker ticker) {
        this.name = name;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .ticker(ticker)
                .recordStats()
                .build();
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> loader) {
        boolean[] loaded = {false};
        V value;
        try {
            value = cache.get(key, k -> {
                loaded[0] = true;
                return loader.apply(k);
            });
        } catch (RuntimeException e) {
            log.error("Cache load failed, cache={}, error={}", name, e.toString());
            throw e;
        }

        if (loaded[0]) {
            log.info("Cache miss, cache={}, cached={}", name, value != null);
        } else {
            log.info("Cache hit, cache={}", name);
        }
        return value;
    }

    @Override
    public void invalidate(K key) {
        boolean removed = cache.asMap().remove(key) != null;
        log.info("Cache entry evicted, cache={}, wasCached={}", name, removed);
    }

    @Override
    public void invalidateAll() {
        long entries = cache.estimatedSize();
        cache.invalidateAll();
        log.info("Cache cleared, cache={}, entries={}", name, entries);
    }

    @Override
    public DataCacheStats stats() {
        CacheStats stats = cache.stats();
        return DataCacheStats.of(stats.hitCount(), stats.missCount(), cache.estimatedSize());
    }
}
