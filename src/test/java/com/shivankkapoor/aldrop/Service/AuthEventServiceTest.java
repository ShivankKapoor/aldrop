package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shivankkapoor.aldrop.Data.AuthEvent;
import com.shivankkapoor.aldrop.Data.AuthEventType;
import com.shivankkapoor.aldrop.Repository.AuthEventRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthEventServiceTest {

    @Mock
    private AuthEventRepository authEventRepository;

    // unused directly - getLocation() never reaches it in these tests, since meridianBaseUrl is
    // never set outside a real Spring context (no @Value resolution here), so the request URI is
    // never absolute and the HTTP call always fails before any response body would be parsed.
    // kept mocked anyway so a null field can't ever NPE if that flow changes.
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthEventService authEventService;

    private final UUID platformId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubSaveToReturnItsArgument() {
        // record() logs the repository's returned id on success, so unstubbed save() calls
        // (which default to returning null) would NPE inside the try block on that log line -
        // swallowed silently by the catch, but it'd mask a real failure as this "success" path.
        // lenient() since recordDoesNotPropagateWhenSaveFails overrides this with doThrow instead.
        lenient().when(authEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordSavesEventWithGivenFields() {
        authEventService.record(platformId, userId, "alice", AuthEventType.LOGIN_SUCCESS,
                "203.0.113.5", "test-agent");

        ArgumentCaptor<AuthEvent> captor = ArgumentCaptor.forClass(AuthEvent.class);
        verify(authEventRepository).save(captor.capture());
        AuthEvent saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPlatformId()).isEqualTo(platformId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAttemptedUsername()).isEqualTo("alice");
        assertThat(saved.getEventType()).isEqualTo(AuthEventType.LOGIN_SUCCESS);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.5");
        assertThat(saved.getUserAgent()).isEqualTo("test-agent");
        // meridian isn't reachable/configured in this unit test - location resolution should
        // degrade to null rather than fail the whole event write.
        assertThat(saved.getCity()).isNull();
        assertThat(saved.getCountry()).isNull();
    }

    @Test
    void recordSkipsLocationLookupAndLeavesCityAndCountryNullWhenIpIsNull() {
        authEventService.record(platformId, userId, null, AuthEventType.LOGIN_SUCCESS, null, "test-agent");

        ArgumentCaptor<AuthEvent> captor = ArgumentCaptor.forClass(AuthEvent.class);
        verify(authEventRepository).save(captor.capture());
        AuthEvent saved = captor.getValue();
        assertThat(saved.getCity()).isNull();
        assertThat(saved.getCountry()).isNull();
    }

    @Test
    void recordAllowsNullUserIdAndAttemptedUsername() {
        authEventService.record(platformId, null, null, AuthEventType.SESSION_INVALID, null, null);

        ArgumentCaptor<AuthEvent> captor = ArgumentCaptor.forClass(AuthEvent.class);
        verify(authEventRepository).save(captor.capture());
        AuthEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getAttemptedUsername()).isNull();
    }

    @Test
    void recordDoesNotPropagateWhenSaveFails() {
        doThrow(new RuntimeException("db unavailable")).when(authEventRepository).save(any());

        assertThatCode(() -> authEventService.record(platformId, userId, null, AuthEventType.LOGOUT, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void recordGeneratesAUniqueIdPerEvent() {
        authEventService.record(platformId, userId, null, AuthEventType.LOGIN_SUCCESS, null, null);
        authEventService.record(platformId, userId, null, AuthEventType.LOGIN_SUCCESS, null, null);

        ArgumentCaptor<AuthEvent> captor = ArgumentCaptor.forClass(AuthEvent.class);
        verify(authEventRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getId()).isNotEqualTo(captor.getAllValues().get(1).getId());
    }
}
