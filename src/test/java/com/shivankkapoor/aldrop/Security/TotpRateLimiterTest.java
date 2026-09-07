package com.shivankkapoor.aldrop.Security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shivankkapoor.aldrop.Exception.TooManyAttemptsException;

class TotpRateLimiterTest {

    private final TotpRateLimiter rateLimiter = new TotpRateLimiter();

    @Test
    void allowsUpToFiveEnableAttemptsThenRejects() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                rateLimiter.checkEnableRateLimit(userId);
            }
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> rateLimiter.checkEnableRateLimit(userId))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void allowsUpToFiveConfirmAttemptsThenRejects() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                rateLimiter.checkConfirmRateLimit(userId);
            }
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> rateLimiter.checkConfirmRateLimit(userId))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void enableAndConfirmLimitsAreIndependentForTheSameUser() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            rateLimiter.checkEnableRateLimit(userId);
        }

        assertThatCode(() -> rateLimiter.checkConfirmRateLimit(userId))
                .doesNotThrowAnyException();
    }

    @Test
    void differentUsersHaveIndependentLimits() {
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            rateLimiter.checkConfirmRateLimit(userOne);
        }
        assertThatThrownBy(() -> rateLimiter.checkConfirmRateLimit(userOne))
                .isInstanceOf(TooManyAttemptsException.class);

        assertThatCode(() -> rateLimiter.checkConfirmRateLimit(userTwo))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsUpToFiveLoginAttemptsThenRejects() {
        String key = UUID.randomUUID() + ":alice";

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                rateLimiter.checkLoginRateLimit(key);
            }
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> rateLimiter.checkLoginRateLimit(key))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void resetLoginRateLimitClearsTheCounter() {
        String key = UUID.randomUUID() + ":alice";

        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLoginRateLimit(key);
        }
        rateLimiter.resetLoginRateLimit(key);

        assertThatCode(() -> rateLimiter.checkLoginRateLimit(key))
                .doesNotThrowAnyException();
    }

    @Test
    void differentLoginKeysHaveIndependentLimits() {
        String keyOne = UUID.randomUUID() + ":alice";
        String keyTwo = UUID.randomUUID() + ":alice";

        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLoginRateLimit(keyOne);
        }
        assertThatThrownBy(() -> rateLimiter.checkLoginRateLimit(keyOne))
                .isInstanceOf(TooManyAttemptsException.class);

        assertThatCode(() -> rateLimiter.checkLoginRateLimit(keyTwo))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsUpToFiveVerifyTotpAttemptsThenRejects() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                rateLimiter.checkVerifyTotpRateLimit(userId);
            }
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> rateLimiter.checkVerifyTotpRateLimit(userId))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void resetVerifyTotpRateLimitClearsTheCounter() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            rateLimiter.checkVerifyTotpRateLimit(userId);
        }
        rateLimiter.resetVerifyTotpRateLimit(userId);

        assertThatCode(() -> rateLimiter.checkVerifyTotpRateLimit(userId))
                .doesNotThrowAnyException();
    }
}
