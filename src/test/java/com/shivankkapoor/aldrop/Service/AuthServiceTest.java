package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Data.Session;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.LoginRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LogoutAllRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LogoutRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.ValidateSessionRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.LoginResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.ValidateSessionResponseDTO;
import com.shivankkapoor.aldrop.Exception.InvalidCredentialsException;
import com.shivankkapoor.aldrop.Exception.InvalidSessionException;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Repository.SessionRepository;
import com.shivankkapoor.aldrop.Repository.UserRepository;
import com.shivankkapoor.aldrop.Security.PasswordHasher;
import com.shivankkapoor.aldrop.Security.TokenGenerator;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private AuthService authService;

    private final UUID platformId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private User activeUser() {
        User user = new User();
        user.setId(userId);
        user.setPlatformId(platformId);
        user.setUsername("alice");
        user.setPasswordHash("hashed-password");
        user.setActive(true);
        return user;
    }

    private Platform platformWithLimit(Integer maxSessionsPerUser) {
        Platform platform = new Platform();
        platform.setId(platformId);
        platform.setSessionTtl(Duration.ofHours(2));
        platform.setMaxSessionsPerUser(maxSessionsPerUser);
        return platform;
    }

    // ---- login ----

    @Test
    void loginSucceedsAndCreatesSessionWithComputedExpiry() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("Alice");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithLimit(null)));
        when(tokenGenerator.generate(anyInt())).thenReturn("generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponseDTO response = authService.login(platformId, request);

        assertThat(response.getToken()).isEqualTo("generated-token");

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPlatformId()).isEqualTo(platformId);
        assertThat(saved.getTokenHash()).isEqualTo("generated-token");
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plus(Duration.ofHours(2)));
        assertThat(response.getExpiresAt()).isEqualTo(saved.getExpiresAt());
    }

    @Test
    void loginLowercasesUsernameBeforeLookup() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("ALICE");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithLimit(null)));
        when(tokenGenerator.generate(anyInt())).thenReturn("generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.login(platformId, request);

        verify(userRepository).findByPlatformIdAndUsername(platformId, "alice");
    }

    @Test
    void throwsInvalidCredentialsWhenUserNotFound() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("ghost");
        request.setPassword("whatever");

        when(userRepository.findByPlatformIdAndUsername(platformId, "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(platformId, request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void throwsInvalidCredentialsWhenUserInactive() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        User inactiveUser = activeUser();
        inactiveUser.setActive(false);
        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authService.login(platformId, request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordHasher, never()).matches(any(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void throwsInvalidCredentialsWhenPasswordWrong() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("wrongpassword");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("wrongpassword", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(platformId, request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void skipsSessionCountCheckWhenMaxSessionsPerUserIsNull() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithLimit(null)));
        when(tokenGenerator.generate(anyInt())).thenReturn("generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.login(platformId, request);

        verify(sessionRepository, never())
                .findByUserIdAndPlatformIdAndExpiresAtAfterOrderByCreatedAtAsc(any(), any(), any());
        verify(sessionRepository, never()).deleteAll(any());
    }

    @Test
    void evictsOldestSessionWhenAtLimit() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        Session oldest = new Session();
        oldest.setId(UUID.randomUUID());
        Session newer = new Session();
        newer.setId(UUID.randomUUID());

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithLimit(2)));
        when(sessionRepository.findByUserIdAndPlatformIdAndExpiresAtAfterOrderByCreatedAtAsc(eq(userId), eq(platformId), any()))
                .thenReturn(List.of(oldest, newer));
        when(tokenGenerator.generate(anyInt())).thenReturn("generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.login(platformId, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Session>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessionRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(oldest);
    }

    @Test
    void doesNotEvictWhenUnderLimit() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        Session existing = new Session();
        existing.setId(UUID.randomUUID());

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithLimit(5)));
        when(sessionRepository.findByUserIdAndPlatformIdAndExpiresAtAfterOrderByCreatedAtAsc(eq(userId), eq(platformId), any()))
                .thenReturn(List.of(existing));
        when(tokenGenerator.generate(anyInt())).thenReturn("generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.login(platformId, request);

        verify(sessionRepository, never()).deleteAll(any());
    }

    @Test
    void evictsMultipleOldestSessionsWhenFarOverLimit() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        Session oldest = new Session();
        oldest.setId(UUID.randomUUID());
        Session secondOldest = new Session();
        secondOldest.setId(UUID.randomUUID());
        Session thirdOldest = new Session();
        thirdOldest.setId(UUID.randomUUID());
        Session newest = new Session();
        newest.setId(UUID.randomUUID());

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithLimit(2)));
        when(sessionRepository.findByUserIdAndPlatformIdAndExpiresAtAfterOrderByCreatedAtAsc(eq(userId), eq(platformId), any()))
                .thenReturn(List.of(oldest, secondOldest, thirdOldest, newest));
        when(tokenGenerator.generate(anyInt())).thenReturn("generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.login(platformId, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Session>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessionRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(oldest, secondOldest, thirdOldest);
    }

    // ---- validate ----

    @Test
    void validateSucceedsForActiveUnexpiredSession() {
        ValidateSessionRequestDTO request = new ValidateSessionRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setTokenHash("valid-token");
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));
        when(sessionRepository.save(session)).thenReturn(session);

        ValidateSessionResponseDTO response = authService.validate(platformId, request);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getExpiresAt()).isEqualTo(session.getExpiresAt());
        verify(sessionRepository).save(session);
    }

    @Test
    void throwsInvalidSessionWhenTokenNotFound() {
        ValidateSessionRequestDTO request = new ValidateSessionRequestDTO();
        request.setToken("unknown-token");

        when(sessionRepository.findByTokenHash("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.validate(platformId, request))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void throwsInvalidSessionWhenPlatformMismatch() {
        ValidateSessionRequestDTO request = new ValidateSessionRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setTokenHash("valid-token");
        session.setUserId(userId);
        session.setPlatformId(UUID.randomUUID());
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.validate(platformId, request))
                .isInstanceOf(InvalidSessionException.class);

        verify(userRepository, never()).findById(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void throwsInvalidSessionWhenExpired() {
        ValidateSessionRequestDTO request = new ValidateSessionRequestDTO();
        request.setToken("expired-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setTokenHash("expired-token");
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        when(sessionRepository.findByTokenHash("expired-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.validate(platformId, request))
                .isInstanceOf(InvalidSessionException.class);

        verify(userRepository, never()).findById(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void throwsInvalidSessionWhenUserInactive() {
        ValidateSessionRequestDTO request = new ValidateSessionRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setTokenHash("valid-token");
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        User inactiveUser = activeUser();
        inactiveUser.setActive(false);

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authService.validate(platformId, request))
                .isInstanceOf(InvalidSessionException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void throwsInvalidSessionWhenSessionsUserNoLongerExists() {
        ValidateSessionRequestDTO request = new ValidateSessionRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setTokenHash("valid-token");
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.validate(platformId, request))
                .isInstanceOf(InvalidSessionException.class);

        verify(sessionRepository, never()).save(any());
    }

    // ---- logout ----

    @Test
    void logoutDeletesMatchingSession() {
        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setPlatformId(platformId);
        session.setUserId(userId);

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));

        authService.logout(platformId, request);

        verify(sessionRepository).delete(session);
    }

    @Test
    void logoutIsNoOpWhenTokenNotFound() {
        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setToken("unknown-token");

        when(sessionRepository.findByTokenHash("unknown-token")).thenReturn(Optional.empty());

        authService.logout(platformId, request);

        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void logoutIsNoOpWhenPlatformMismatch() {
        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setPlatformId(UUID.randomUUID());
        session.setUserId(userId);

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));

        authService.logout(platformId, request);

        verify(sessionRepository, never()).delete(any());
    }

    // ---- logoutAll ----

    @Test
    void logoutAllDeletesAllSessionsForUser() {
        LogoutAllRequestDTO request = new LogoutAllRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setPlatformId(platformId);
        session.setUserId(userId);

        Session other = new Session();
        other.setId(UUID.randomUUID());

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));
        when(sessionRepository.findByUserIdAndPlatformId(userId, platformId)).thenReturn(List.of(session, other));

        authService.logoutAll(platformId, request);

        verify(sessionRepository).deleteAll(List.of(session, other));
    }

    @Test
    void logoutAllIsNoOpWhenTokenNotFound() {
        LogoutAllRequestDTO request = new LogoutAllRequestDTO();
        request.setToken("unknown-token");

        when(sessionRepository.findByTokenHash("unknown-token")).thenReturn(Optional.empty());

        authService.logoutAll(platformId, request);

        verify(sessionRepository, never()).findByUserIdAndPlatformId(any(), any());
        verify(sessionRepository, never()).deleteAll(any());
    }

    @Test
    void logoutAllIsNoOpWhenPlatformMismatch() {
        LogoutAllRequestDTO request = new LogoutAllRequestDTO();
        request.setToken("valid-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setPlatformId(UUID.randomUUID());
        session.setUserId(userId);

        when(sessionRepository.findByTokenHash("valid-token")).thenReturn(Optional.of(session));

        authService.logoutAll(platformId, request);

        verify(sessionRepository, never()).findByUserIdAndPlatformId(any(), any());
        verify(sessionRepository, never()).deleteAll(any());
    }
}
