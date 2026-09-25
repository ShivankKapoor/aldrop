package com.shivankkapoor.aldrop.Service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.shivankkapoor.aldrop.Cache.AfterCommit;
import com.shivankkapoor.aldrop.Cache.CachedPlatform;
import com.shivankkapoor.aldrop.Cache.DataCache;
import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Security.TokenHasher;

@Service
public class PlatformLookup {

    private final DataCache<String, CachedPlatform> cache;
    private final PlatformRepository platformRepository;
    private final TokenHasher tokenHasher;

    public PlatformLookup(DataCache<String, CachedPlatform> cache, PlatformRepository platformRepository,
            TokenHasher tokenHasher) {
        this.cache = cache;
        this.platformRepository = platformRepository;
        this.tokenHasher = tokenHasher;
    }

    public Optional<CachedPlatform> findActiveByApiKey(String apiKey) {
        return Optional.ofNullable(cache.get(tokenHasher.hash(apiKey), key ->
                platformRepository.findByApiKey(apiKey)
                        .filter(Platform::isActive)
                        .map(CachedPlatform::from)
                        .orElse(null)));
    }

    public void evict(String apiKey) {
        String key = tokenHasher.hash(apiKey);
        AfterCommit.run(() -> cache.invalidate(key));
    }
}
