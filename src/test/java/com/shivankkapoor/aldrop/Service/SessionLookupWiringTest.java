package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.shivankkapoor.aldrop.Cache.CacheConfig;
import com.shivankkapoor.aldrop.Cache.DataCacheRegistry;
import com.shivankkapoor.aldrop.Data.Session;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Repository.SessionRepository;
import com.shivankkapoor.aldrop.Repository.UserRepository;
import com.shivankkapoor.aldrop.Security.TokenHasher;

class SessionLookupWiringTest {

    @Test
    void sessionLookupIsWiredToTheSessionCacheAndTheOthersAreUntouched() {
        SessionRepository repository = mock(SessionRepository.class);
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(UUID.randomUUID());
        session.setPlatformId(UUID.randomUUID());
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        when(repository.findByTokenHash("token-hash")).thenReturn(Optional.of(session));

        new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                    context.getBeanFactory().registerSingleton("sessionRepository", repository);
                    context.getBeanFactory().registerSingleton("userRepository", mock(UserRepository.class));
                    context.getBeanFactory().registerSingleton("platformRepository", mock(PlatformRepository.class));
                })
                .withUserConfiguration(CacheConfig.class, TokenHasher.class, PlatformLookup.class,
                        UserLookup.class, SessionLookup.class, DataCacheRegistry.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SessionLookup.class).findActiveByTokenHash("token-hash")).isPresent();
                    DataCacheRegistry registry = context.getBean(DataCacheRegistry.class);
                    assertThat(registry.stats().get("sessionCache").misses()).isEqualTo(1);
                    assertThat(registry.stats().get("platformCache").misses()).isZero();
                    assertThat(registry.stats().get("userActiveCache").misses()).isZero();
                });
    }
}
