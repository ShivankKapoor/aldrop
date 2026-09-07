package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.shivankkapoor.aldrop.Data.TotpSession;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.ConfirmTotpRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.EnableTotpRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LoginRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LogoutAllRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LogoutRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.ValidateSessionRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.VerifyTotpRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.ConfirmTotpResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.EnableTotpResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.LoginResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.ValidateSessionResponseDTO;
import com.shivankkapoor.aldrop.Exception.InvalidCredentialsException;
import com.shivankkapoor.aldrop.Exception.InvalidSessionException;
import com.shivankkapoor.aldrop.Exception.InvalidTotpException;
import com.shivankkapoor.aldrop.Exception.TooManyAttemptsException;
import com.shivankkapoor.aldrop.Exception.TotpAlreadyEnabledException;
import com.shivankkapoor.aldrop.Exception.TotpNotAvailableException;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Repository.SessionRepository;
import com.shivankkapoor.aldrop.Repository.TotpSessionRepository;
import com.shivankkapoor.aldrop.Repository.UserRepository;
import com.shivankkapoor.aldrop.Security.PasswordHasher;
import com.shivankkapoor.aldrop.Security.TokenGenerator;
import com.shivankkapoor.aldrop.Security.TokenHasher;
import com.shivankkapoor.aldrop.Security.TotpManager;
import com.shivankkapoor.aldrop.Security.TotpRateLimiter;

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
    private TokenHasher tokenHasher;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private TotpSessionRepository totpSessionRepository;

    @Mock
    private TotpManager totpManager;

    @Mock
    private TotpRateLimiter totpRateLimiter;

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

    private User activeUserWithTotpEnabled(String seed, String... backupCodes) {
        User user = activeUser();
        user.setTotpEnabled(true);
        user.setTotpSeed(seed);
        user.setTotpBackupCodes(backupCodes);
        return user;
    }

    private Platform platformWithLimit(Integer maxSessionsPerUser) {
        Platform platform = new Platform();
        platform.setId(platformId);
        platform.setSessionTtl(Duration.ofHours(2));
        platform.setMaxSessionsPerUser(maxSessionsPerUser);
        return platform;
    }

    private Platform platformWithTotpAvailable(boolean totpAvailable) {
        Platform platform = platformWithLimit(null);
        platform.setTotpAvailable(totpAvailable);
        return platform;
    }

    private TotpSession activeTotpSession() {
        TotpSession totpSession = new TotpSession();
        totpSession.setId(UUID.randomUUID());
        totpSession.setTokenHash("hashed-totp-token");
        totpSession.setUserId(userId);
        totpSession.setPlatformId(platformId);
        totpSession.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        totpSession.setAttemptCount(0);
        return totpSession;
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
        when(tokenHasher.hash("generated-token")).thenReturn("hashed-generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponseDTO response = authService.login(platformId, request);

        assertThat(response.getToken()).isEqualTo("generated-token");

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPlatformId()).isEqualTo(platformId);
        assertThat(saved.getTokenHash()).isEqualTo("hashed-generated-token");
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
        when(tokenHasher.hash("generated-token")).thenReturn("hashed-generated-token");
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
        when(tokenHasher.hash("generated-token")).thenReturn("hashed-generated-token");
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
        when(tokenHasher.hash("generated-token")).thenReturn("hashed-generated-token");
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
        when(tokenHasher.hash("generated-token")).thenReturn("hashed-generated-token");
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
        when(tokenHasher.hash("generated-token")).thenReturn("hashed-generated-token");
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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));
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

        when(tokenHasher.hash("unknown-token")).thenReturn("hashed-unknown-token");
        when(sessionRepository.findByTokenHash("hashed-unknown-token")).thenReturn(Optional.empty());

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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));

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

        when(tokenHasher.hash("expired-token")).thenReturn("hashed-expired-token");
        when(sessionRepository.findByTokenHash("hashed-expired-token")).thenReturn(Optional.of(session));

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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));
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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));
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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));

        authService.logout(platformId, request);

        verify(sessionRepository).delete(session);
    }

    @Test
    void logoutIsNoOpWhenTokenNotFound() {
        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setToken("unknown-token");

        when(tokenHasher.hash("unknown-token")).thenReturn("hashed-unknown-token");
        when(sessionRepository.findByTokenHash("hashed-unknown-token")).thenReturn(Optional.empty());

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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));

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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));
        when(sessionRepository.findByUserIdAndPlatformId(userId, platformId)).thenReturn(List.of(session, other));

        authService.logoutAll(platformId, request);

        verify(sessionRepository).deleteAll(List.of(session, other));
    }

    @Test
    void logoutAllIsNoOpWhenTokenNotFound() {
        LogoutAllRequestDTO request = new LogoutAllRequestDTO();
        request.setToken("unknown-token");

        when(tokenHasher.hash("unknown-token")).thenReturn("hashed-unknown-token");
        when(sessionRepository.findByTokenHash("hashed-unknown-token")).thenReturn(Optional.empty());

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

        when(tokenHasher.hash("valid-token")).thenReturn("hashed-valid-token");
        when(sessionRepository.findByTokenHash("hashed-valid-token")).thenReturn(Optional.of(session));

        authService.logoutAll(platformId, request);

        verify(sessionRepository, never()).findByUserIdAndPlatformId(any(), any());
        verify(sessionRepository, never()).deleteAll(any());
    }

    // ---- login with TOTP ----

    @Test
    void loginIssuesTotpChallengeWhenPlatformAndUserBothHaveTotpEnabled() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice"))
                .thenReturn(Optional.of(activeUserWithTotpEnabled("SEED123")));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithTotpAvailable(true)));
        when(tokenGenerator.generate(anyInt())).thenReturn("totp-challenge-token");
        when(tokenHasher.hash("totp-challenge-token")).thenReturn("hashed-totp-challenge-token");
        when(totpSessionRepository.save(any(TotpSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponseDTO response = authService.login(platformId, request);

        assertThat(response.getToken()).isNull();
        assertThat(response.getTotpToken()).isEqualTo("totp-challenge-token");

        ArgumentCaptor<TotpSession> captor = ArgumentCaptor.forClass(TotpSession.class);
        verify(totpSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getPlatformId()).isEqualTo(platformId);
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hashed-totp-challenge-token");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void loginCreatesSessionWhenPlatformSupportsTotpButUserHasNotEnabledIt() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches("correcthorse", "hashed-password")).thenReturn(true);
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithTotpAvailable(true)));
        when(tokenGenerator.generate(anyInt())).thenReturn("generated-token");
        when(tokenHasher.hash("generated-token")).thenReturn("hashed-generated-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponseDTO response = authService.login(platformId, request);

        assertThat(response.getToken()).isEqualTo("generated-token");
        assertThat(response.getTotpToken()).isNull();
        verify(totpSessionRepository, never()).save(any());
    }

    // ---- verifyTotp ----

    @Test
    void verifyTotpSucceedsWithValidCodeAndCreatesSession() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("123456");

        TotpSession totpSession = activeTotpSession();

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUserWithTotpEnabled("SEED123")));
        when(totpManager.verifyCode("SEED123", "123456")).thenReturn(true);
        when(totpSessionRepository.save(any(TotpSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithTotpAvailable(true)));
        when(tokenGenerator.generate(anyInt())).thenReturn("session-token");
        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponseDTO response = authService.verifyTotp(platformId, request);

        assertThat(response.getToken()).isEqualTo("session-token");
        assertThat(totpSession.getConsumedAt()).isNotNull();
    }

    @Test
    void verifyTotpSucceedsWithBackupCodeWhenTotpCodeInvalid() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("backup-code-1");

        TotpSession totpSession = activeTotpSession();
        User user = activeUserWithTotpEnabled("SEED123", "backup-code-1", "backup-code-2");

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpManager.verifyCode("SEED123", "backup-code-1")).thenReturn(false);
        when(totpSessionRepository.save(any(TotpSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithTotpAvailable(true)));
        when(tokenGenerator.generate(anyInt())).thenReturn("session-token");
        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponseDTO response = authService.verifyTotp(platformId, request);

        assertThat(response.getToken()).isEqualTo("session-token");
        assertThat(user.getTotpBackupCodes()).containsExactly("backup-code-2");
        verify(userRepository).save(user);
    }

    @Test
    void verifyTotpThrowsWhenCodeAndBackupCodeBothInvalid() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("wrong-code");

        TotpSession totpSession = activeTotpSession();

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUserWithTotpEnabled("SEED123", "backup-code-1")));
        when(totpManager.verifyCode("SEED123", "wrong-code")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);

        assertThat(totpSession.getAttemptCount()).isEqualTo(1);
        verify(totpSessionRepository).save(totpSession);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void verifyTotpThrowsWhenTotpTokenNotFound() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("unknown-token");
        request.setCode("123456");

        when(tokenHasher.hash("unknown-token")).thenReturn("hashed-unknown-token");
        when(totpSessionRepository.findByTokenHash("hashed-unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);
    }

    @Test
    void verifyTotpThrowsWhenPlatformMismatch() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("123456");

        TotpSession totpSession = activeTotpSession();
        totpSession.setPlatformId(UUID.randomUUID());

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));

        assertThatThrownBy(() -> authService.verifyTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void verifyTotpThrowsWhenAlreadyConsumed() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("123456");

        TotpSession totpSession = activeTotpSession();
        totpSession.setConsumedAt(OffsetDateTime.now().minusMinutes(1));

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));

        assertThatThrownBy(() -> authService.verifyTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);
    }

    @Test
    void verifyTotpThrowsWhenExpired() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("123456");

        TotpSession totpSession = activeTotpSession();
        totpSession.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));

        assertThatThrownBy(() -> authService.verifyTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);
    }

    @Test
    void verifyTotpThrowsWhenAttemptLimitReached() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("123456");

        TotpSession totpSession = activeTotpSession();
        totpSession.setAttemptCount(5);

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));

        assertThatThrownBy(() -> authService.verifyTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void verifyTotpThrowsWhenUserInactive() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken("totp-token");
        request.setCode("123456");

        TotpSession totpSession = activeTotpSession();
        User inactiveUser = activeUserWithTotpEnabled("SEED123");
        inactiveUser.setActive(false);

        when(tokenHasher.hash("totp-token")).thenReturn("hashed-totp-token");
        when(totpSessionRepository.findByTokenHash("hashed-totp-token")).thenReturn(Optional.of(totpSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authService.verifyTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);

        verify(totpManager, never()).verifyCode(any(), any());
    }

    // ---- enableTotp ----

    @Test
    void enableTotpGeneratesAndSavesSeedForActiveSessionUser() {
        EnableTotpRequestDTO request = new EnableTotpRequestDTO();
        request.setToken("session-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.findByTokenHash("hashed-session-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithTotpAvailable(true)));
        when(totpManager.generateSecret()).thenReturn("NEWSECRET");
        when(totpManager.buildOtpAuthUri("NEWSECRET", "alice")).thenReturn("otpauth://totp/alice?secret=NEWSECRET");

        EnableTotpResponseDTO response = authService.enableTotp(platformId, request);

        assertThat(response.getSecret()).isEqualTo("NEWSECRET");
        assertThat(response.getOtpAuthUri()).isEqualTo("otpauth://totp/alice?secret=NEWSECRET");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getTotpSeed()).isEqualTo("NEWSECRET");
        verify(totpRateLimiter).checkEnableRateLimit(userId);
    }

    @Test
    void enableTotpThrowsWhenRateLimited() {
        EnableTotpRequestDTO request = new EnableTotpRequestDTO();
        request.setToken("session-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.findByTokenHash("hashed-session-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));
        doThrow(new TooManyAttemptsException()).when(totpRateLimiter).checkEnableRateLimit(userId);

        assertThatThrownBy(() -> authService.enableTotp(platformId, request))
                .isInstanceOf(TooManyAttemptsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void enableTotpThrowsWhenPlatformDoesNotSupportTotp() {
        EnableTotpRequestDTO request = new EnableTotpRequestDTO();
        request.setToken("session-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.findByTokenHash("hashed-session-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithTotpAvailable(false)));

        assertThatThrownBy(() -> authService.enableTotp(platformId, request))
                .isInstanceOf(TotpNotAvailableException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void enableTotpThrowsWhenAlreadyEnabled() {
        EnableTotpRequestDTO request = new EnableTotpRequestDTO();
        request.setToken("session-token");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.findByTokenHash("hashed-session-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUserWithTotpEnabled("EXISTING")));
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(platformWithTotpAvailable(true)));

        assertThatThrownBy(() -> authService.enableTotp(platformId, request))
                .isInstanceOf(TotpAlreadyEnabledException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void enableTotpThrowsWhenSessionInvalid() {
        EnableTotpRequestDTO request = new EnableTotpRequestDTO();
        request.setToken("unknown-token");

        when(tokenHasher.hash("unknown-token")).thenReturn("hashed-unknown-token");
        when(sessionRepository.findByTokenHash("hashed-unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.enableTotp(platformId, request))
                .isInstanceOf(InvalidSessionException.class);
    }

    // ---- confirmTotp ----

    @Test
    void confirmTotpEnablesAndReturnsBackupCodes() {
        ConfirmTotpRequestDTO request = new ConfirmTotpRequestDTO();
        request.setToken("session-token");
        request.setCode("123456");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        User user = activeUser();
        user.setTotpSeed("PENDINGSECRET");

        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.findByTokenHash("hashed-session-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpManager.verifyCode("PENDINGSECRET", "123456")).thenReturn(true);
        when(tokenGenerator.generate(anyInt())).thenReturn("backup-code");

        ConfirmTotpResponseDTO response = authService.confirmTotp(platformId, request);

        assertThat(response.getBackupCodes()).hasSize(8);
        assertThat(user.isTotpEnabled()).isTrue();
        assertThat(user.getTotpBackupCodes()).hasSize(8);
        verify(userRepository).save(user);
        verify(totpRateLimiter).checkConfirmRateLimit(userId);
    }

    @Test
    void confirmTotpThrowsWhenRateLimited() {
        ConfirmTotpRequestDTO request = new ConfirmTotpRequestDTO();
        request.setToken("session-token");
        request.setCode("123456");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        User user = activeUser();
        user.setTotpSeed("PENDINGSECRET");

        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.findByTokenHash("hashed-session-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new TooManyAttemptsException()).when(totpRateLimiter).checkConfirmRateLimit(userId);

        assertThatThrownBy(() -> authService.confirmTotp(platformId, request))
                .isInstanceOf(TooManyAttemptsException.class);

        assertThat(user.isTotpEnabled()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmTotpThrowsWhenCodeInvalid() {
        ConfirmTotpRequestDTO request = new ConfirmTotpRequestDTO();
        request.setToken("session-token");
        request.setCode("wrong-code");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setPlatformId(platformId);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        User user = activeUser();
        user.setTotpSeed("PENDINGSECRET");

        when(tokenHasher.hash("session-token")).thenReturn("hashed-session-token");
        when(sessionRepository.findByTokenHash("hashed-session-token")).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpManager.verifyCode("PENDINGSECRET", "wrong-code")).thenReturn(false);

        assertThatThrownBy(() -> authService.confirmTotp(platformId, request))
                .isInstanceOf(InvalidTotpException.class);

        assertThat(user.isTotpEnabled()).isFalse();
        verify(userRepository, never()).save(any());
    }
}
