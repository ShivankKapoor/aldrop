package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shivankkapoor.aldrop.Cache.CachedPlatform;
import com.shivankkapoor.aldrop.Cache.CaffeineDataCache;
import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Security.TokenHasher;

@ExtendWith(MockitoExtension.class)
class PlatformLookupTest {

    @Mock
    private PlatformRepository platformRepository;

    private PlatformLookup platformLookup;
    private final UUID platformId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        platformLookup = new PlatformLookup(new CaffeineDataCache<>("platformCache", Duration.ofMinutes(1), 10),
                platformRepository, new TokenHasher());
    }

    private Platform platform(boolean active, boolean requireDeviceBinding) {
        Platform platform = new Platform();
        platform.setId(platformId);
        platform.setApiKey("api-key");
        platform.setActive(active);
        platform.setRequireDeviceBinding(requireDeviceBinding);
        return platform;
    }

    @Test
    void loadsAnActivePlatformOnceAndServesLaterCallsFromTheCache() {
        when(platformRepository.findByApiKey("api-key")).thenReturn(Optional.of(platform(true, true)));

        Optional<CachedPlatform> first = platformLookup.findActiveByApiKey("api-key");
        Optional<CachedPlatform> second = platformLookup.findActiveByApiKey("api-key");

        assertThat(first).contains(new CachedPlatform(platformId, true, true));
        assertThat(second).isEqualTo(first);
        verify(platformRepository, times(1)).findByApiKey("api-key");
    }

    @Test
    void returnsEmptyForAnUnknownKeyAndDoesNotCacheTheMiss() {
        when(platformRepository.findByApiKey("unknown")).thenReturn(Optional.empty());

        assertThat(platformLookup.findActiveByApiKey("unknown")).isEmpty();
        assertThat(platformLookup.findActiveByApiKey("unknown")).isEmpty();

        verify(platformRepository, times(2)).findByApiKey("unknown");
    }

    @Test
    void returnsEmptyForAnInactivePlatformAndDoesNotCacheIt() {
        when(platformRepository.findByApiKey("api-key")).thenReturn(Optional.of(platform(false, false)));

        assertThat(platformLookup.findActiveByApiKey("api-key")).isEmpty();
        assertThat(platformLookup.findActiveByApiKey("api-key")).isEmpty();

        verify(platformRepository, times(2)).findByApiKey("api-key");
    }

    @Test
    void aNewlyCreatedPlatformIsUsableRightAfterAMiss() {
        when(platformRepository.findByApiKey("api-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(platform(true, false)));

        assertThat(platformLookup.findActiveByApiKey("api-key")).isEmpty();
        assertThat(platformLookup.findActiveByApiKey("api-key")).isPresent();
    }

    @Test
    void evictForcesTheNextLookupToReloadFromTheDatabase() {
        when(platformRepository.findByApiKey("api-key"))
                .thenReturn(Optional.of(platform(true, false)))
                .thenReturn(Optional.of(platform(false, false)));

        assertThat(platformLookup.findActiveByApiKey("api-key")).isPresent();

        platformLookup.evict("api-key");

        assertThat(platformLookup.findActiveByApiKey("api-key")).isEmpty();
    }

    @Test
    void evictOnlyRemovesTheGivenKey() {
        Platform other = platform(true, false);
        other.setApiKey("other-key");
        when(platformRepository.findByApiKey("api-key")).thenReturn(Optional.of(platform(true, false)));
        when(platformRepository.findByApiKey("other-key")).thenReturn(Optional.of(other));
        platformLookup.findActiveByApiKey("api-key");
        platformLookup.findActiveByApiKey("other-key");

        platformLookup.evict("api-key");
        platformLookup.findActiveByApiKey("other-key");

        verify(platformRepository, times(1)).findByApiKey("other-key");
    }

    @Test
    void evictInsideATransactionWaitsForTheCommit() {
        when(platformRepository.findByApiKey("api-key")).thenReturn(Optional.of(platform(true, false)));
        platformLookup.findActiveByApiKey("api-key");

        TransactionSynchronizationManager.initSynchronization();
        try {
            platformLookup.evict("api-key");

            platformLookup.findActiveByApiKey("api-key");
            verify(platformRepository, times(1)).findByApiKey("api-key");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        platformLookup.findActiveByApiKey("api-key");
        verify(platformRepository, times(2)).findByApiKey("api-key");
    }

    @Test
    void evictInsideARolledBackTransactionKeepsTheEntry() {
        when(platformRepository.findByApiKey("api-key")).thenReturn(Optional.of(platform(true, false)));
        platformLookup.findActiveByApiKey("api-key");

        TransactionSynchronizationManager.initSynchronization();
        try {
            platformLookup.evict("api-key");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        platformLookup.findActiveByApiKey("api-key");
        verify(platformRepository, times(1)).findByApiKey("api-key");
    }
}
