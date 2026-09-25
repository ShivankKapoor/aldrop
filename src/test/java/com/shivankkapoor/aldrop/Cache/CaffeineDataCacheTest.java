package com.shivankkapoor.aldrop.Cache;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Ticker;

class CaffeineDataCacheTest {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final CaffeineDataCache<String, String> cache = new CaffeineDataCache<>(TTL, 100, ticker);

    @Test
    void loadsOnMissAndServesFromCacheOnHit() {
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get("k", key -> "v" + loads.incrementAndGet());
        String second = cache.get("k", key -> "v" + loads.incrementAndGet());

        assertThat(first).isEqualTo("v1");
        assertThat(second).isEqualTo("v1");
        assertThat(loads).hasValue(1);
    }

    @Test
    void doesNotCacheANullLoadResult() {
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("k", key -> { loads.incrementAndGet(); return null; })).isNull();
        assertThat(cache.get("k", key -> { loads.incrementAndGet(); return null; })).isNull();

        assertThat(loads).hasValue(2);
    }

    @Test
    void aNullLoadDoesNotBlockALaterSuccessfulLoad() {
        assertThat(cache.get("k", key -> null)).isNull();

        assertThat(cache.get("k", key -> "now-exists")).isEqualTo("now-exists");
    }

    @Test
    void reloadsAfterTheTtlEvenWhenTheEntryWasJustRead() {
        AtomicInteger loads = new AtomicInteger();
        cache.get("k", key -> "v" + loads.incrementAndGet());

        nanos.addAndGet(TTL.minusSeconds(1).toNanos());
        cache.get("k", key -> "v" + loads.incrementAndGet());
        assertThat(loads).hasValue(1);

        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        String reloaded = cache.get("k", key -> "v" + loads.incrementAndGet());

        assertThat(reloaded).isEqualTo("v2");
    }

    @Test
    void invalidateRemovesOnlyThatKey() {
        AtomicInteger loads = new AtomicInteger();
        cache.get("a", key -> "a" + loads.incrementAndGet());
        cache.get("b", key -> "b" + loads.incrementAndGet());

        cache.invalidate("a");

        assertThat(cache.get("a", key -> "a-reloaded")).isEqualTo("a-reloaded");
        assertThat(cache.get("b", key -> "b-reloaded")).isEqualTo("b2");
    }

    @Test
    void invalidateAllRemovesEveryEntry() {
        cache.get("a", key -> "a");
        cache.get("b", key -> "b");

        cache.invalidateAll();

        assertThat(cache.get("a", key -> "a-reloaded")).isEqualTo("a-reloaded");
        assertThat(cache.get("b", key -> "b-reloaded")).isEqualTo("b-reloaded");
    }

    @Test
    void statsCountHitsMissesAndSize() {
        cache.get("a", key -> "a");
        cache.get("a", key -> "a");
        cache.get("a", key -> "a");
        cache.get("b", key -> null);

        DataCacheStats stats = cache.stats();

        assertThat(stats.hits()).isEqualTo(2);
        assertThat(stats.misses()).isEqualTo(2);
        assertThat(stats.hitRate()).isEqualTo(0.5);
        assertThat(stats.size()).isEqualTo(1);
    }

    @Test
    void hitRateIsZeroBeforeAnyRequest() {
        assertThat(cache.stats().hitRate()).isZero();
    }

    @Test
    void staysWithinTheMaximumSize() throws InterruptedException {
        CaffeineDataCache<Integer, String> bounded = new CaffeineDataCache<>(Duration.ofMinutes(1), 10);

        for (int i = 0; i < 1000; i++) {
            bounded.get(i, key -> "v" + key);
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (bounded.stats().size() > 10 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(bounded.stats().size()).isLessThanOrEqualTo(10);
    }

    @Test
    void concurrentMissesForTheSameKeyShareOneLoad() throws Exception {
        CaffeineDataCache<String, String> shared = new CaffeineDataCache<>(TTL, 100);
        int threads = 16;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return shared.get("k", key -> {
                        loads.incrementAndGet();
                        pause(200);
                        return "v";
                    });
                }));
            }
            ready.await();
            go.countDown();

            for (Future<String> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("v");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(loads).hasValue(1);
    }

    @Test
    void aFailingLoaderIsNotCachedAndDoesNotBlockALaterLoad() {
        assertThatThrownBy(() -> cache.get("k", key -> {
            throw new IllegalStateException("database down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cache.get("k", key -> "recovered")).isEqualTo("recovered");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
