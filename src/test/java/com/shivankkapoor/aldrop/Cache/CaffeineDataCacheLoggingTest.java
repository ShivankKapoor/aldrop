package com.shivankkapoor.aldrop.Cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class CaffeineDataCacheLoggingTest {

    private static final String SECRET_KEY = "super-secret-token-hash";

    private final CaffeineDataCache<String, String> cache =
            new CaffeineDataCache<>("testCache", Duration.ofMinutes(1), 10);
    private final Logger logger = (Logger) LoggerFactory.getLogger(CaffeineDataCache.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsAMissThenAHitAtInfoLevel() {
        cache.get(SECRET_KEY, key -> "value");
        cache.get(SECRET_KEY, key -> "value");

        assertThat(messages()).containsExactly(
                "Cache miss, cache=testCache, cached=true",
                "Cache hit, cache=testCache");
        assertThat(appender.list).allMatch(event -> event.getLevel() == Level.INFO);
    }

    @Test
    void logsThatAMissWasNotCachedWhenTheLoaderFindsNothing() {
        cache.get(SECRET_KEY, key -> null);

        assertThat(messages()).containsExactly("Cache miss, cache=testCache, cached=false");
    }

    @Test
    void logsWhetherAnEvictionRemovedAnEntry() {
        cache.get(SECRET_KEY, key -> "value");
        appender.list.clear();

        cache.invalidate(SECRET_KEY);
        cache.invalidate(SECRET_KEY);

        assertThat(messages()).containsExactly(
                "Cache entry evicted, cache=testCache, wasCached=true",
                "Cache entry evicted, cache=testCache, wasCached=false");
    }

    @Test
    void logsHowManyEntriesAClearRemoved() {
        cache.get("a", key -> "1");
        cache.get("b", key -> "2");
        appender.list.clear();

        cache.invalidateAll();

        assertThat(messages()).containsExactly("Cache cleared, cache=testCache, entries=2");
    }

    @Test
    void logsALoaderFailureAtErrorLevelWithoutAStackTraceAndStillThrows() {
        assertThatThrownBy(() -> cache.get(SECRET_KEY, key -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .isEqualTo("Cache load failed, cache=testCache, error=java.lang.IllegalStateException: boom");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void neverLogsTheKey() {
        cache.get(SECRET_KEY, key -> "value");
        cache.get(SECRET_KEY, key -> "value");
        cache.get("other-" + SECRET_KEY, key -> null);
        cache.invalidate(SECRET_KEY);
        assertThatThrownBy(() -> cache.get(SECRET_KEY + "-failing", key -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        cache.invalidateAll();

        assertThat(messages()).isNotEmpty().noneMatch(message -> message.contains(SECRET_KEY));
    }
}
