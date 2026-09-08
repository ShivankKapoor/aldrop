package com.shivankkapoor.aldrop.Security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryTotpReplayGuardTest {

    private InMemoryTotpReplayGuard replayGuard;

    @BeforeEach
    void setUp() {
        replayGuard = new InMemoryTotpReplayGuard(new TokenHasher());
    }

    @Test
    void claimsAnUnusedCode() {
        assertThat(replayGuard.claimCode(UUID.randomUUID(), "123456")).isTrue();
    }

    @Test
    void rejectsTheSameCodeOnSecondUse() {
        UUID userId = UUID.randomUUID();

        assertThat(replayGuard.claimCode(userId, "123456")).isTrue();
        assertThat(replayGuard.claimCode(userId, "123456")).isFalse();
    }

    @Test
    void scopesClaimsPerUser() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertThat(replayGuard.claimCode(alice, "123456")).isTrue();
        assertThat(replayGuard.claimCode(bob, "123456")).isTrue();
    }

    @Test
    void allowsADifferentCodeForTheSameUser() {
        UUID userId = UUID.randomUUID();

        assertThat(replayGuard.claimCode(userId, "123456")).isTrue();
        assertThat(replayGuard.claimCode(userId, "654321")).isTrue();
    }

    @Test
    void onlyOneOfManyConcurrentClaimsOnTheSameCodeSucceeds() throws Exception {
        UUID userId = UUID.randomUUID();
        int threads = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger claimed = new AtomicInteger(0);

        try {
            List<Callable<Void>> tasks = java.util.Collections.nCopies(threads, () -> {
                if (replayGuard.claimCode(userId, "123456")) {
                    claimed.incrementAndGet();
                }
                return null;
            });
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(claimed.get()).isEqualTo(1);
    }
}
