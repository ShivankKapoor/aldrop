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
import com.shivankkapoor.aldrop.Cache.CachedUser;
import com.shivankkapoor.aldrop.Cache.DataCacheRegistry;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Repository.UserRepository;
import com.shivankkapoor.aldrop.Security.TokenHasher;

class UserLookupWiringTest {

    @Test
    void userLookupIsWiredToTheUserCacheAndTheRegistryReportsIt() {
        UserRepository repository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setActive(true);
        when(repository.findById(userId)).thenReturn(Optional.of(user));

        new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                    context.getBeanFactory().registerSingleton("userRepository", repository);
                    context.getBeanFactory().registerSingleton("platformRepository", mock(PlatformRepository.class));
                })
                .withUserConfiguration(CacheConfig.class, TokenHasher.class, PlatformLookup.class,
                        UserLookup.class, DataCacheRegistry.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(UserLookup.class).findActiveById(userId))
                            .contains(new CachedUser(userId, "alice"));
                    assertThat(context.getBean(DataCacheRegistry.class).stats().get("userActiveCache").misses())
                            .isEqualTo(1);
                    assertThat(context.getBean(DataCacheRegistry.class).stats().get("platformCache").misses())
                            .isZero();
                });
    }
}
