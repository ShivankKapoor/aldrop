package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.shivankkapoor.aldrop.Cache.CacheConfig;
import com.shivankkapoor.aldrop.Cache.CachedPlatform;
import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Security.TokenHasher;

class PlatformLookupWiringTest {

    @Test
    void platformLookupIsWiredToThePlatformCacheNotTheUserCache() {
        PlatformRepository repository = mock(PlatformRepository.class);
        Platform platform = new Platform();
        platform.setId(UUID.randomUUID());
        platform.setApiKey("api-key");
        platform.setActive(true);
        when(repository.findByApiKey("api-key")).thenReturn(Optional.of(platform));

        new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                    context.getBeanFactory().registerSingleton("platformRepository", repository);
                })
                .withUserConfiguration(CacheConfig.class, TokenHasher.class, PlatformLookup.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PlatformLookup.class).findActiveByApiKey("api-key"))
                            .contains(CachedPlatform.from(platform));
                });
    }
}
