package com.shivankkapoor.aldrop.Service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.shivankkapoor.aldrop.Data.AuthEventType;
import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Data.Session;
import com.shivankkapoor.aldrop.Data.TotpSession;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.Exception.DeviceBindingRequiredException;
import com.shivankkapoor.aldrop.Exception.InvalidCredentialsException;
import com.shivankkapoor.aldrop.Exception.InvalidSessionException;
import com.shivankkapoor.aldrop.Exception.InvalidTotpException;
import com.shivankkapoor.aldrop.Exception.PlatformNotFoundException;
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
import com.shivankkapoor.aldrop.Security.TotpReplayGuard;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_TOTP_ATTEMPTS = 5;
    private static final int BACKUP_CODE_COUNT = 8;
    private static final int BACKUP_CODE_BYTES = 6;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TotpSessionRepository totpSessionRepository;

    @Autowired
    private TotpManager totpManager;

    @Autowired
    private TotpRateLimiter totpRateLimiter;

    @Autowired
    private TotpReplayGuard totpReplayGuard;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private AuthEventService authEventService;

    public LoginResponseDTO login(UUID platformId, LoginRequestDTO requestDTO) {
        String username = requestDTO.getUsername().toLowerCase(Locale.ROOT);
        String loginRateLimitKey = platformId + ":" + username;
        try {
            totpRateLimiter.checkLoginRateLimit(loginRateLimitKey);
        } catch (TooManyAttemptsException e) {
            authEventService.record(platformId, null, username, AuthEventType.LOGIN_RATE_LIMITED,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw e;
        }

        Optional<User> maybeUser = userRepository.findByPlatformIdAndUsername(platformId, username);
        boolean passwordMatches = passwordHasher.matches(
                requestDTO.getPassword(),
                maybeUser.map(User::getPasswordHash).orElse(null));

        AuthEventType failureType = null;
        if (maybeUser.isEmpty()) {
            failureType = AuthEventType.LOGIN_FAILED_UNKNOWN_USER;
        } else if (!passwordMatches) {
            failureType = AuthEventType.LOGIN_FAILED_BAD_PASSWORD;
        } else if (!maybeUser.get().isActive()) {
            failureType = AuthEventType.LOGIN_FAILED_INACTIVE;
        }

        if (failureType != null) {
            log.warn("Login rejected, platformId={}, username={}", platformId, username);
            authEventService.record(platformId, maybeUser.map(User::getId).orElse(null),
                    maybeUser.isEmpty() ? username : null, failureType,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw new InvalidCredentialsException();
        }

        User user = maybeUser.get();
        totpRateLimiter.resetLoginRateLimit(loginRateLimitKey);

        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));

        if (platform.isTotpAvailable() && user.isTotpEnabled()) {
            LoginResponseDTO challenge = sessionService.issueTotpChallenge(user, platformId);
            log.info("Login requires TOTP, userId={}, platformId={}", user.getId(), platformId);
            authEventService.record(platformId, user.getId(), null, AuthEventType.TOTP_CHALLENGE_ISSUED,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            return challenge;
        }

        LoginResponseDTO response = sessionService.createSessionResponse(user, platform, platformId,
                requestDTO.getIpAddress(), requestDTO.getUserAgent());
        log.info("Login succeeded, userId={}, platformId={}", user.getId(), platformId);
        authEventService.record(platformId, user.getId(), null, AuthEventType.LOGIN_SUCCESS,
                requestDTO.getIpAddress(), requestDTO.getUserAgent());
        return response;
    }

    @Transactional(noRollbackFor = InvalidTotpException.class)
    public LoginResponseDTO verifyTotp(UUID platformId, VerifyTotpRequestDTO requestDTO) {
        TotpSession totpSession = totpSessionRepository.findByTokenHash(tokenHasher.hash(requestDTO.getTotpToken()))
                .filter(ts -> ts.getPlatformId().equals(platformId))
                .orElseThrow(() -> {
                    authEventService.record(platformId, null, null, AuthEventType.TOTP_SESSION_INVALID,
                            requestDTO.getIpAddress(), requestDTO.getUserAgent());
                    return new InvalidTotpException();
                });

        OffsetDateTime now = OffsetDateTime.now();
        if (totpSession.getConsumedAt() != null || totpSession.getExpiresAt().isBefore(now)
                || totpSession.getAttemptCount() >= MAX_TOTP_ATTEMPTS) {
            log.warn("TOTP verification rejected, totpSessionId={}, platformId={}", totpSession.getId(), platformId);
            authEventService.record(platformId, totpSession.getUserId(), null, AuthEventType.TOTP_SESSION_INVALID,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw new InvalidTotpException();
        }

        User user = userRepository.findById(totpSession.getUserId())
                .orElseThrow(() -> {
                    authEventService.record(platformId, totpSession.getUserId(), null, AuthEventType.TOTP_SESSION_INVALID,
                            requestDTO.getIpAddress(), requestDTO.getUserAgent());
                    return new InvalidTotpException();
                });

        if (!user.isActive()) {
            authEventService.record(platformId, user.getId(), null, AuthEventType.LOGIN_FAILED_INACTIVE,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw new InvalidTotpException();
        }

        try {
            totpRateLimiter.checkVerifyTotpRateLimit(user.getId());
        } catch (TooManyAttemptsException e) {
            authEventService.record(platformId, user.getId(), null, AuthEventType.TOTP_RATE_LIMITED,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw e;
        }

        boolean validCode = (totpManager.verifyCode(user.getTotpSeed(), requestDTO.getCode())
                && totpReplayGuard.claimCode(user.getId(), requestDTO.getCode()))
                || consumeBackupCodeIfMatches(user, requestDTO.getCode());

        if (!validCode) {
            totpSession.setAttemptCount(totpSession.getAttemptCount() + 1);
            totpSessionRepository.save(totpSession);
            log.warn("TOTP code invalid, totpSessionId={}, userId={}, platformId={}",
                    totpSession.getId(), user.getId(), platformId);
            authEventService.record(platformId, user.getId(), null, AuthEventType.TOTP_FAILED_CODE,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw new InvalidTotpException();
        }

        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));

        if (platform.isRequireDeviceBinding()
                && (isBlank(requestDTO.getIpAddress()) || isBlank(requestDTO.getUserAgent()))) {
            authEventService.record(platformId, user.getId(), null, AuthEventType.DEVICE_BINDING_REJECTED,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw new DeviceBindingRequiredException();
        }

        int consumed = totpSessionRepository.markConsumedIfUnconsumed(totpSession.getId(), now);
        if (consumed == 0) {
            log.warn("TOTP verification lost the consumption race, totpSessionId={}, userId={}, platformId={}",
                    totpSession.getId(), user.getId(), platformId);
            authEventService.record(platformId, user.getId(), null, AuthEventType.TOTP_SESSION_INVALID,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw new InvalidTotpException();
        }
        totpRateLimiter.resetVerifyTotpRateLimit(user.getId());

        LoginResponseDTO response = sessionService.createSessionResponse(user, platform, platformId,
                requestDTO.getIpAddress(), requestDTO.getUserAgent());
        log.info("TOTP verification succeeded, totpSessionId={}, userId={}, platformId={}",
                totpSession.getId(), user.getId(), platformId);
        authEventService.record(platformId, user.getId(), null, AuthEventType.LOGIN_SUCCESS_TOTP,
                requestDTO.getIpAddress(), requestDTO.getUserAgent());
        return response;
    }

    @Transactional
    public EnableTotpResponseDTO enableTotp(UUID platformId, EnableTotpRequestDTO requestDTO) {
        Session session = resolveActiveSession(platformId, requestDTO.getToken(), "totp-enable");
        User user = resolveActiveUser(session, "totp-enable");
        totpRateLimiter.checkEnableRateLimit(user.getId());

        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));

        if (!platform.isTotpAvailable()) {
            throw new TotpNotAvailableException();
        }
        if (user.isTotpEnabled()) {
            throw new TotpAlreadyEnabledException();
        }

        String secret = totpManager.generateSecret();
        user.setTotpSeed(secret);
        userRepository.save(user);
        log.info("TOTP enable started, userId={}, platformId={}", user.getId(), platformId);

        return new EnableTotpResponseDTO(secret, totpManager.buildOtpAuthUri(secret, user.getUsername()));
    }

    @Transactional
    public ConfirmTotpResponseDTO confirmTotp(UUID platformId, ConfirmTotpRequestDTO requestDTO) {
        Session session = resolveActiveSession(platformId, requestDTO.getToken(), "totp-confirm");
        User user = resolveActiveUser(session, "totp-confirm");
        totpRateLimiter.checkConfirmRateLimit(user.getId());

        if (user.isTotpEnabled()) {
            throw new TotpAlreadyEnabledException();
        }

        if (!totpManager.verifyCode(user.getTotpSeed(), requestDTO.getCode())
                || !totpReplayGuard.claimCode(user.getId(), requestDTO.getCode())) {
            log.warn("TOTP confirm rejected, invalid or reused code, userId={}, platformId={}",
                    user.getId(), platformId);
            throw new InvalidTotpException();
        }

        String[] backupCodes = generateBackupCodes();
        user.setTotpEnabled(true);
        user.setTotpBackupCodes(backupCodes);
        userRepository.save(user);
        log.info("TOTP enabled, userId={}, platformId={}", user.getId(), platformId);

        return new ConfirmTotpResponseDTO(Arrays.asList(backupCodes));
    }

    @Transactional
    public ValidateSessionResponseDTO validate(UUID platformId, ValidateSessionRequestDTO requestDTO) {
        Session session;
        try {
            session = resolveActiveSession(platformId, requestDTO.getToken(), "validate");
        } catch (InvalidSessionException e) {
            authEventService.record(platformId, null, null, AuthEventType.SESSION_INVALID,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw e;
        }

        User user;
        try {
            user = resolveActiveUser(session, "validate");
        } catch (InvalidSessionException e) {
            authEventService.record(platformId, session.getUserId(), null, AuthEventType.SESSION_INVALID,
                    requestDTO.getIpAddress(), requestDTO.getUserAgent());
            throw e;
        }

        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));
        if (platform.isRequireDeviceBinding()) {
            try {
                enforceDeviceBinding(session, requestDTO.getIpAddress(), requestDTO.getUserAgent());
            } catch (DeviceBindingRequiredException | InvalidSessionException e) {
                authEventService.record(platformId, session.getUserId(), null, AuthEventType.DEVICE_BINDING_REJECTED,
                        requestDTO.getIpAddress(), requestDTO.getUserAgent());
                throw e;
            }
        }

        Session saved = sessionRepository.save(session);
        log.info("Session validated, sessionId={}, userId={}, platformId={}", saved.getId(), user.getId(), platformId);

        return new ValidateSessionResponseDTO(saved.getUserId(), user.getUsername(), saved.getExpiresAt());
    }

    @Transactional
    public void logout(UUID platformId, LogoutRequestDTO requestDTO) {
        sessionRepository.findByTokenHash(tokenHasher.hash(requestDTO.getToken()))
                .filter(session -> session.getPlatformId().equals(platformId))
                .ifPresentOrElse(
                        session -> {
                            sessionRepository.delete(session);
                            log.info("Logout succeeded, sessionId={}, userId={}, platformId={}",
                                    session.getId(), session.getUserId(), platformId);
                            authEventService.record(platformId, session.getUserId(), null, AuthEventType.LOGOUT,
                                    session.getIpAddress(), session.getUserAgent());
                        },
                        () -> log.info("Logout no-op, no matching session for platformId={}", platformId)
                );
    }

    @Transactional
    public void logoutAll(UUID platformId, LogoutAllRequestDTO requestDTO) {
        sessionRepository.findByTokenHash(tokenHasher.hash(requestDTO.getToken()))
                .filter(session -> session.getPlatformId().equals(platformId))
                .ifPresentOrElse(
                        session -> {
                            List<Session> sessions = sessionRepository
                                    .findByUserIdAndPlatformId(session.getUserId(), platformId);
                            sessionRepository.deleteAll(sessions);
                            log.info("Logout-all succeeded, userId={}, platformId={}, sessionsRevoked={}",
                                    session.getUserId(), platformId, sessions.size());
                            authEventService.record(platformId, session.getUserId(), null, AuthEventType.LOGOUT_ALL,
                                    session.getIpAddress(), session.getUserAgent());
                        },
                        () -> log.info("Logout-all no-op, no matching session for platformId={}", platformId)
                );
    }

    private Session resolveActiveSession(UUID platformId, String token, String action) {
        Session session = sessionRepository.findByTokenHash(tokenHasher.hash(token))
                .orElseThrow(InvalidSessionException::new);

        if (!session.getPlatformId().equals(platformId) || session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("Session resolution rejected for {}, platformId={}, sessionId={}", action, platformId, session.getId());
            throw new InvalidSessionException();
        }

        return session;
    }

    private void enforceDeviceBinding(Session session, String ipAddress, String userAgent) {
        if (isBlank(ipAddress) || isBlank(userAgent)) {
            throw new DeviceBindingRequiredException();
        }
        if (!ipAddress.equals(session.getIpAddress()) || !userAgent.equals(session.getUserAgent())) {
            log.warn("Session resolution rejected for validate, device mismatch, sessionId={}", session.getId());
            throw new InvalidSessionException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private User resolveActiveUser(Session session, String action) {
        User user = userRepository.findById(session.getUserId())
                .orElseThrow(InvalidSessionException::new);

        if (!user.isActive()) {
            log.warn("Session resolution rejected for {}, user inactive, sessionId={}, userId={}",
                    action, session.getId(), user.getId());
            throw new InvalidSessionException();
        }

        return user;
    }

    private String[] generateBackupCodes() {
        String[] codes = new String[BACKUP_CODE_COUNT];
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes[i] = tokenGenerator.generate(BACKUP_CODE_BYTES);
        }
        return codes;
    }

    private boolean consumeBackupCodeIfMatches(User user, String code) {
        return userRepository.consumeBackupCodeIfPresent(user.getId(), code) > 0;
    }
}
