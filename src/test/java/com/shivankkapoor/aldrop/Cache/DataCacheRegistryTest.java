package com.shivankkapoor.aldrop.Cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataCacheRegistryTest {

    private final DataCache<String, String> first = new CaffeineDataCache<>(Duration.ofMinutes(1), 10);
    private final DataCache<String, String> second = new CaffeineDataCache<>(Duration.ofMinutes(1), 10);
    private final DataCacheRegistry registry = new DataCacheRegistry(List.of(first, second));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(CacheConfig.class, DataCacheRegistry.class);

    @Test
    void clearAllEmptiesEveryRegisteredCache() {
        first.get("a", key -> "a");
        second.get("b", key -> "b");

        registry.clearAll();

        assertThat(first.get("a", key -> "reloaded")).isEqualTo("reloaded");
        assertThat(second.get("b", key -> "reloaded")).isEqualTo("reloaded");
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearAllClearsTheCachesBuiltByCacheConfig() {
        contextRunner.run(context -> {
            DataCache<UUID, Boolean> userActive = context.getBean("userActiveCache", DataCache.class);
            UUID userId = UUID.randomUUID();
            userActive.get(userId, key -> true);

            context.getBean(DataCacheRegistry.class).clearAll();

            assertThat(userActive.get(userId, key -> false)).isFalse();
        });
    }

    @Test
    void cacheConfigIsSkippedWhenAnotherBackendIsSelected() {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class)
                .withPropertyValues("aldrop.cache.backend=redis")
                .run(context -> assertThat(context).doesNotHaveBean(CacheConfig.class));
    }
}
