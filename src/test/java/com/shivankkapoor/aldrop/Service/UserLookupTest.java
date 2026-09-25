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

import com.shivankkapoor.aldrop.Cache.CachedUser;
import com.shivankkapoor.aldrop.Cache.CaffeineDataCache;
import com.shivankkapoor.aldrop.Cache.DataCache;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserLookupTest {

    @Mock
    private UserRepository userRepository;

    private DataCache<UUID, CachedUser> cache;
    private UserLookup userLookup;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cache = new CaffeineDataCache<>("userActiveCache", Duration.ofMinutes(1), 10);
        userLookup = new UserLookup(cache, userRepository);
    }

    private User user(boolean active) {
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setTotpSeed("seed");
        user.setActive(active);
        return user;
    }

    @Test
    void loadsAnActiveUserOnceAndServesLaterCallsFromTheCache() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true)));

        Optional<CachedUser> first = userLookup.findActiveById(userId);
        Optional<CachedUser> second = userLookup.findActiveById(userId);

        assertThat(first).contains(new CachedUser(userId, "alice"));
        assertThat(second).isEqualTo(first);
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void returnsEmptyForAnInactiveUserAndDoesNotCacheIt() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(false)));

        assertThat(userLookup.findActiveById(userId)).isEmpty();
        assertThat(userLookup.findActiveById(userId)).isEmpty();

        verify(userRepository, times(2)).findById(userId);
    }

    @Test
    void returnsEmptyForAMissingUserAndDoesNotCacheIt() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(userLookup.findActiveById(userId)).isEmpty();
        assertThat(userLookup.findActiveById(userId)).isEmpty();

        verify(userRepository, times(2)).findById(userId);
    }

    @Test
    void aDeactivatedUserIsRejectedOnceTheCacheIsCleared() {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user(true)))
                .thenReturn(Optional.of(user(false)));

        assertThat(userLookup.findActiveById(userId)).isPresent();

        cache.invalidateAll();

        assertThat(userLookup.findActiveById(userId)).isEmpty();
    }

    @Test
    void theCachedValueHoldsNoCredentials() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true)));

        CachedUser cached = userLookup.findActiveById(userId).orElseThrow();

        assertThat(cached.toString()).doesNotContain("hash").doesNotContain("seed");
    }
}
