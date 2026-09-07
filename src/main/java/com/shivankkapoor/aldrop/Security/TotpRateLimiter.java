package com.shivankkapoor.aldrop.Security;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shivankkapoor.aldrop.Exception.TooManyAttemptsException;
import org.springframework.stereotype.Component;

@Component
public class TotpRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Cache<UUID, AtomicInteger> enableAttempts = newCache();
    private final Cache<UUID, AtomicInteger> confirmAttempts = newCache();

    public void checkEnableRateLimit(UUID userId) {
        checkRateLimit(enableAttempts, userId);
    }

    public void checkConfirmRateLimit(UUID userId) {
        checkRateLimit(confirmAttempts, userId);
    }

    private void checkRateLimit(Cache<UUID, AtomicInteger> attempts, UUID userId) {
        int count = attempts.asMap()
                .computeIfAbsent(userId, key -> new AtomicInteger(0))
                .incrementAndGet();

        if (count > MAX_ATTEMPTS) {
            throw new TooManyAttemptsException();
        }
    }

    private static Cache<UUID, AtomicInteger> newCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(WINDOW)
                .build();
    }
}
