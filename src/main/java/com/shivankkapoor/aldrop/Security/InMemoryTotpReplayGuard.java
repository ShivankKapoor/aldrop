package com.shivankkapoor.aldrop.Security;

import java.time.Duration;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-process {@link TotpReplayGuard}. Claims are lost on restart and are not shared between
 * instances, which is acceptable while Aldrop runs as a single container. Swap in a Redis-backed
 * implementation by setting {@code aldrop.replay-guard.backend=redis}.
 */
@Component
@ConditionalOnProperty(name = "aldrop.replay-guard.backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryTotpReplayGuard implements TotpReplayGuard {

    // A code is accepted until the end of the following time step, so it can still be live up to
    // 60 seconds after it is used. 120 seconds leaves room for clock skew on top of that.
    private static final Duration CLAIM_TTL = Duration.ofSeconds(120);
    private static final long MAX_CLAIMS = 100_000;

    private final TokenHasher tokenHasher;

    public InMemoryTotpReplayGuard(TokenHasher tokenHasher) {
        this.tokenHasher = tokenHasher;
    }

    private final Cache<String, Boolean> claimedCodes = Caffeine.newBuilder()
            .expireAfterWrite(CLAIM_TTL)
            .maximumSize(MAX_CLAIMS)
            .build();

    @Override
    public boolean claimCode(UUID userId, String code) {
        String key = userId + ":" + tokenHasher.hash(code);
        return claimedCodes.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }
}
