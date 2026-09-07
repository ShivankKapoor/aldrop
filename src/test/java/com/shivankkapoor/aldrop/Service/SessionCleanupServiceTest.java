package com.shivankkapoor.aldrop.Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shivankkapoor.aldrop.Repository.SessionRepository;
import com.shivankkapoor.aldrop.Repository.TotpSessionRepository;

@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private TotpSessionRepository totpSessionRepository;

    @InjectMocks
    private SessionCleanupService sessionCleanupService;

    @Test
    void deleteExpiredSessionsPurgesBothSessionsAndTotpSessions() {
        when(sessionRepository.deleteAllByExpiresAtBefore(any(OffsetDateTime.class))).thenReturn(3);
        when(totpSessionRepository.deleteAllByExpiresAtBefore(any(OffsetDateTime.class))).thenReturn(2);

        sessionCleanupService.deleteExpiredSessions();

        verify(sessionRepository).deleteAllByExpiresAtBefore(any(OffsetDateTime.class));
        verify(totpSessionRepository).deleteAllByExpiresAtBefore(any(OffsetDateTime.class));
    }
}
