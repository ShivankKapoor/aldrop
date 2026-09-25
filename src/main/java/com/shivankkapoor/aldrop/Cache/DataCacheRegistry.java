package com.shivankkapoor.aldrop.Cache;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DataCacheRegistry {

    private final List<DataCache<?, ?>> caches;

    public DataCacheRegistry(List<DataCache<?, ?>> caches) {
        this.caches = caches;
    }

    public void clearAll() {
        caches.forEach(DataCache::invalidateAll);
    }
}
