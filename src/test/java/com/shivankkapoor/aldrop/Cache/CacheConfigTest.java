package com.shivankkapoor.aldrop.Cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CacheConfigTest {

    private static final List<String> CACHE_NAMES = List.of("platformCache", "userActiveCache", "sessionCache");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(CacheConfig.class);

    @SuppressWarnings("unchecked")
    private DataCache<Object, Object> cache(AssertableApplicationContext context, String name) {
        return context.getBean(name, DataCache.class);
    }

    private boolean reloadsWithin(DataCache<Object, Object> cache, Object key, long seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if ("second".equals(cache.get(key, k -> "second"))) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    @Test
    void createsAllThreeCaches() {
        contextRunner.run(context -> assertThat(context).hasBean("platformCache")
                .hasBean("userActiveCache").hasBean("sessionCache"));
    }

    @Test
    void defaultsKeepEntriesForLongerThanAFewHundredMilliseconds() {
        contextRunner.run(context -> {
            for (String name : CACHE_NAMES) {
                DataCache<Object, Object> cache = cache(context, name);
                cache.get("k", key -> "first");

                Thread.sleep(300);

                assertThat(cache.get("k", key -> "second")).as(name).isEqualTo("first");
            }
        });
    }

    @Test
    void eachTtlPropertyOnlyAffectsItsOwnCache() {
        contextRunner.withPropertyValues("aldrop.cache.platform-ttl=100ms").run(context -> {
            for (String name : CACHE_NAMES) {
                cache(context, name).get("k", key -> "first");
            }

            assertThat(reloadsWithin(cache(context, "platformCache"), "k", 3)).isTrue();

            assertThat(cache(context, "userActiveCache").get("k", key -> "second")).isEqualTo("first");
            assertThat(cache(context, "sessionCache").get("k", key -> "second")).isEqualTo("first");
        });
    }

    @Test
    void userActiveAndSessionTtlPropertiesAreBoundToTheirOwnCaches() {
        contextRunner.withPropertyValues("aldrop.cache.user-active-ttl=100ms", "aldrop.cache.session-ttl=100ms")
                .run(context -> {
                    cache(context, "platformCache").get("k", key -> "first");
                    cache(context, "userActiveCache").get("k", key -> "first");
                    cache(context, "sessionCache").get("k", key -> "first");

                    assertThat(reloadsWithin(cache(context, "userActiveCache"), "k", 3)).isTrue();
                    assertThat(reloadsWithin(cache(context, "sessionCache"), "k", 3)).isTrue();
                    assertThat(cache(context, "platformCache").get("k", key -> "second")).isEqualTo("first");
                });
    }

    @Test
    void maxSizePropertiesBoundTheirOwnCaches() {
        contextRunner.withPropertyValues("aldrop.cache.platform-max-size=5", "aldrop.cache.user-active-max-size=5",
                "aldrop.cache.session-max-size=5").run(context -> {
                    for (String name : CACHE_NAMES) {
                        DataCache<Object, Object> cache = cache(context, name);
                        for (int i = 0; i < 500; i++) {
                            cache.get(i, key -> "v");
                        }
                    }

                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    for (String name : CACHE_NAMES) {
                        while (cache(context, name).stats().size() > 5 && System.nanoTime() < deadline) {
                            Thread.sleep(10);
                        }
                        assertThat(cache(context, name).stats().size()).as(name).isLessThanOrEqualTo(5);
                    }
                });
    }

    @Test
    void anUnsetMaxSizeStillAllowsMoreThanAHandfulOfEntries() {
        contextRunner.run(context -> {
            DataCache<Object, Object> cache = cache(context, "platformCache");
            for (int i = 0; i < 50; i++) {
                cache.get(i, key -> "v");
            }

            assertThat(cache.stats().size()).isEqualTo(50);
        });
    }
}
