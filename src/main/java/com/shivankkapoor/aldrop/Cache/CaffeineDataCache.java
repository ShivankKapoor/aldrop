package com.shivankkapoor.aldrop.Cache;

import java.time.Duration;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

public class CaffeineDataCache<K, V> implements DataCache<K, V> {

    private final Cache<K, V> cache;

    public CaffeineDataCache(Duration ttl, long maxSize) {
        this(ttl, maxSize, Ticker.systemTicker());
    }

    CaffeineDataCache(Duration ttl, long maxSize, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .ticker(ticker)
                .recordStats()
                .build();
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> loader) {
        return cache.get(key, loader);
    }

    @Override
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
