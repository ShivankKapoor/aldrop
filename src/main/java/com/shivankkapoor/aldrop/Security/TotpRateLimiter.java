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
    private final Cache<String, AtomicInteger> loginAttempts = newCache();
    private final Cache<UUID, AtomicInteger> verifyTotpAttempts = newCache();

    public void checkEnableRateLimit(UUID userId) {
        checkRateLimit(enableAttempts, userId);
    }

    public void checkConfirmRateLimit(UUID userId) {
        checkRateLimit(confirmAttempts, userId);
    }

    public void checkLoginRateLimit(String platformAndUsernameKey) {
        checkRateLimit(loginAttempts, platformAndUsernameKey);
    }

    public void resetLoginRateLimit(String platformAndUsernameKey) {
        loginAttempts.invalidate(platformAndUsernameKey);
    }

    public void checkVerifyTotpRateLimit(UUID userId) {
        checkRateLimit(verifyTotpAttempts, userId);
    }

    public void resetVerifyTotpRateLimit(UUID userId) {
        verifyTotpAttempts.invalidate(userId);
    }

    private <K> void checkRateLimit(Cache<K, AtomicInteger> attempts, K key) {
        int count = attempts.asMap()
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (count > MAX_ATTEMPTS) {
            throw new TooManyAttemptsException();
        }
    }

    private static <K> Cache<K, AtomicInteger> newCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(WINDOW)
                .build();
    }
}
