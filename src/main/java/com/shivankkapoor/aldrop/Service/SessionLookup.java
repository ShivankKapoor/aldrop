package com.shivankkapoor.aldrop.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.shivankkapoor.aldrop.Cache.AfterCommit;
import com.shivankkapoor.aldrop.Cache.CachedSession;
import com.shivankkapoor.aldrop.Cache.DataCache;
import com.shivankkapoor.aldrop.Repository.SessionRepository;

@Service
public class SessionLookup {

    private final DataCache<String, CachedSession> cache;
    private final SessionRepository sessionRepository;

    public SessionLookup(DataCache<String, CachedSession> cache, SessionRepository sessionRepository) {
        this.cache = cache;
        this.sessionRepository = sessionRepository;
    }

    public Optional<CachedSession> findActiveByTokenHash(String tokenHash) {
        return Optional.ofNullable(cache.get(tokenHash, key ->
                sessionRepository.findByTokenHash(key)
                        .filter(session -> session.getExpiresAt().isAfter(OffsetDateTime.now()))
                        .map(CachedSession::from)
                        .orElse(null)));
    }

    public void evict(String tokenHash) {
        AfterCommit.run(() -> cache.invalidate(tokenHash));
    }
}
