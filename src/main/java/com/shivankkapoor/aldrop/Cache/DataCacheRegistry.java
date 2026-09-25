package com.shivankkapoor.aldrop.Cache;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

@Component
public class DataCacheRegistry {

    private final Map<String, DataCache<?, ?>> caches;

    public DataCacheRegistry(Map<String, DataCache<?, ?>> caches) {
        this.caches = caches;
    }

    public void clearAll() {
        caches.values().forEach(DataCache::invalidateAll);
    }

    public Map<String, DataCacheStats> stats() {
        Map<String, DataCacheStats> stats = new TreeMap<>();
        caches.forEach((name, cache) -> stats.put(name, cache.stats()));
        return stats;
    }
}
