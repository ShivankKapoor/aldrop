package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shivankkapoor.aldrop.Cache.CachedSession;
import com.shivankkapoor.aldrop.Cache.CaffeineDataCache;
import com.shivankkapoor.aldrop.Data.Session;
import com.shivankkapoor.aldrop.Repository.SessionRepository;

@ExtendWith(MockitoExtension.class)
class SessionLookupTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionLookup sessionLookup;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID platformId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionLookup = new SessionLookup(new CaffeineDataCache<>("sessionCache", Duration.ofMinutes(1), 10), sessionRepository);
    }

    private Session session(OffsetDateTime expiresAt) {
        Session session = new Session();
        session.setId(sessionId);
        session.setTokenHash("token-hash");
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(expiresAt);
        session.setIpAddress("203.0.113.5");
        session.setUserAgent("agent");
        return session;
    }

    @Test
    void loadsALiveSessionOnceAndServesLaterCallsFromTheCache() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
        when(sessionRepository.findByTokenHash("token-hash")).thenReturn(Optional.of(session(expiresAt)));

        Optional<CachedSession> first = sessionLookup.findActiveByTokenHash("token-hash");
        Optional<CachedSession> second = sessionLookup.findActiveByTokenHash("token-hash");

        assertThat(first).contains(new CachedSession(sessionId, userId, platformId, expiresAt, "203.0.113.5", "agent"));
        assertThat(second).isEqualTo(first);
        verify(sessionRepository, times(1)).findByTokenHash("token-hash");
    }

    @Test
    void returnsEmptyForAnUnknownTokenAndDoesNotCacheTheMiss() {
        when(sessionRepository.findByTokenHash("unknown")).thenReturn(Optional.empty());

        assertThat(sessionLookup.findActiveByTokenHash("unknown")).isEmpty();
        assertThat(sessionLookup.findActiveByTokenHash("unknown")).isEmpty();

        verify(sessionRepository, times(2)).findByTokenHash("unknown");
    }

    @Test
    void returnsEmptyForAnExpiredSessionAndDoesNotCacheIt() {
        when(sessionRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(session(OffsetDateTime.now().minusMinutes(1))));

        assertThat(sessionLookup.findActiveByTokenHash("token-hash")).isEmpty();
        assertThat(sessionLookup.findActiveByTokenHash("token-hash")).isEmpty();

        verify(sessionRepository, times(2)).findByTokenHash("token-hash");
    }

    @Test
    void evictForcesTheNextLookupToReloadAndSeeTheRevocation() {
        when(sessionRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(session(OffsetDateTime.now().plusHours(1))))
                .thenReturn(Optional.empty());

        assertThat(sessionLookup.findActiveByTokenHash("token-hash")).isPresent();

        sessionLookup.evict("token-hash");

        assertThat(sessionLookup.findActiveByTokenHash("token-hash")).isEmpty();
    }

    @Test
    void evictOnlyRemovesTheGivenToken() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
        Session other = session(expiresAt);
        other.setTokenHash("other-hash");
        when(sessionRepository.findByTokenHash("token-hash")).thenReturn(Optional.of(session(expiresAt)));
        when(sessionRepository.findByTokenHash("other-hash")).thenReturn(Optional.of(other));
        sessionLookup.findActiveByTokenHash("token-hash");
        sessionLookup.findActiveByTokenHash("other-hash");

        sessionLookup.evict("token-hash");
        sessionLookup.findActiveByTokenHash("other-hash");

        verify(sessionRepository, times(1)).findByTokenHash("other-hash");
    }
}
