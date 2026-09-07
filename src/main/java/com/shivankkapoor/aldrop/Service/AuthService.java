package com.shivankkapoor.aldrop.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
import com.shivankkapoor.aldrop.Exception.PlatformNotFoundException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int SESSION_TOKEN_BYTES = 32;
    private static final Duration TOTP_SESSION_TTL = Duration.ofMinutes(5);
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

    public LoginResponseDTO login(UUID platformId, LoginRequestDTO requestDTO) {
        String username = requestDTO.getUsername().toLowerCase(Locale.ROOT);

        User user = userRepository.findByPlatformIdAndUsername(platformId, username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive() || !passwordHasher.matches(requestDTO.getPassword(), user.getPasswordHash())) {
            log.warn("Login rejected, platformId={}, username={}", platformId, username);
            throw new InvalidCredentialsException();
        }

        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));

        if (platform.isTotpAvailable() && user.isTotpEnabled()) {
            LoginResponseDTO challenge = issueTotpChallenge(user, platformId);
            log.info("Login requires TOTP, userId={}, platformId={}", user.getId(), platformId);
            return challenge;
        }

        LoginResponseDTO response = createSessionResponse(user, platform, platformId);
        log.info("Login succeeded, userId={}, platformId={}", user.getId(), platformId);
        return response;
    }

    public LoginResponseDTO verifyTotp(UUID platformId, VerifyTotpRequestDTO requestDTO) {
        TotpSession totpSession = totpSessionRepository.findByTokenHash(tokenHasher.hash(requestDTO.getTotpToken()))
                .filter(ts -> ts.getPlatformId().equals(platformId))
                .orElseThrow(InvalidTotpException::new);

        OffsetDateTime now = OffsetDateTime.now();
        if (totpSession.getConsumedAt() != null || totpSession.getExpiresAt().isBefore(now)
                || totpSession.getAttemptCount() >= MAX_TOTP_ATTEMPTS) {
            log.warn("TOTP verification rejected, totpSessionId={}, platformId={}", totpSession.getId(), platformId);
            throw new InvalidTotpException();
        }

        User user = userRepository.findById(totpSession.getUserId())
                .orElseThrow(InvalidTotpException::new);

        if (!user.isActive()) {
            throw new InvalidTotpException();
        }

        boolean validCode = totpManager.verifyCode(user.getTotpSeed(), requestDTO.getCode())
                || consumeBackupCodeIfMatches(user, requestDTO.getCode());

        if (!validCode) {
            totpSession.setAttemptCount(totpSession.getAttemptCount() + 1);
            totpSessionRepository.save(totpSession);
            log.warn("TOTP code invalid, totpSessionId={}, userId={}, platformId={}",
                    totpSession.getId(), user.getId(), platformId);
            throw new InvalidTotpException();
        }

        totpSession.setConsumedAt(now);
        totpSessionRepository.save(totpSession);

        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));

        LoginResponseDTO response = createSessionResponse(user, platform, platformId);
        log.info("TOTP verification succeeded, totpSessionId={}, userId={}, platformId={}",
                totpSession.getId(), user.getId(), platformId);
        return response;
    }

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

    public ConfirmTotpResponseDTO confirmTotp(UUID platformId, ConfirmTotpRequestDTO requestDTO) {
        Session session = resolveActiveSession(platformId, requestDTO.getToken(), "totp-confirm");
        User user = resolveActiveUser(session, "totp-confirm");
        totpRateLimiter.checkConfirmRateLimit(user.getId());

        if (!totpManager.verifyCode(user.getTotpSeed(), requestDTO.getCode())) {
            log.warn("TOTP confirm rejected, invalid code, userId={}, platformId={}", user.getId(), platformId);
            throw new InvalidTotpException();
        }

        String[] backupCodes = generateBackupCodes();
        user.setTotpEnabled(true);
        user.setTotpBackupCodes(backupCodes);
        userRepository.save(user);
        log.info("TOTP enabled, userId={}, platformId={}", user.getId(), platformId);

        return new ConfirmTotpResponseDTO(Arrays.asList(backupCodes));
    }

    public ValidateSessionResponseDTO validate(UUID platformId, ValidateSessionRequestDTO requestDTO) {
        Session session = resolveActiveSession(platformId, requestDTO.getToken(), "validate");
        User user = resolveActiveUser(session, "validate");

        Session saved = sessionRepository.save(session);
        log.info("Session validated, sessionId={}, userId={}, platformId={}", saved.getId(), user.getId(), platformId);

        return new ValidateSessionResponseDTO(saved.getUserId(), saved.getExpiresAt());
    }

    public void logout(UUID platformId, LogoutRequestDTO requestDTO) {
        sessionRepository.findByTokenHash(tokenHasher.hash(requestDTO.getToken()))
                .filter(session -> session.getPlatformId().equals(platformId))
                .ifPresentOrElse(
                        session -> {
                            sessionRepository.delete(session);
                            log.info("Logout succeeded, sessionId={}, userId={}, platformId={}",
                                    session.getId(), session.getUserId(), platformId);
                        },
                        () -> log.info("Logout no-op, no matching session for platformId={}", platformId)
                );
    }

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
                        },
                        () -> log.info("Logout-all no-op, no matching session for platformId={}", platformId)
                );
    }

    private LoginResponseDTO createSessionResponse(User user, Platform platform, UUID platformId) {
        OffsetDateTime now = OffsetDateTime.now();
        Integer maxSessionsPerUser = platform.getMaxSessionsPerUser();
        if (maxSessionsPerUser != null) {
            List<Session> activeSessions = sessionRepository
                    .findByUserIdAndPlatformIdAndExpiresAtAfterOrderByCreatedAtAsc(user.getId(), platformId, now);
            int numToEvict = activeSessions.size() - maxSessionsPerUser + 1;
            if (numToEvict > 0) {
                List<Session> toEvict = activeSessions.subList(0, numToEvict);
                sessionRepository.deleteAll(toEvict);
                log.info("Evicted {} oldest session(s) for userId={}, platformId={} to respect maxSessionsPerUser={}",
                        toEvict.size(), user.getId(), platformId, maxSessionsPerUser);
            }
        }

        String rawToken = tokenGenerator.generate(SESSION_TOKEN_BYTES);

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setTokenHash(tokenHasher.hash(rawToken));
        session.setUserId(user.getId());
        session.setPlatformId(platformId);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(platform.getSessionTtl()));

        Session saved = sessionRepository.save(session);
        log.info("Session created, sessionId={}, userId={}, platformId={}", saved.getId(), user.getId(), platformId);

        return new LoginResponseDTO(rawToken, null, saved.getExpiresAt());
    }

    private LoginResponseDTO issueTotpChallenge(User user, UUID platformId) {
        String rawToken = tokenGenerator.generate(SESSION_TOKEN_BYTES);
        OffsetDateTime now = OffsetDateTime.now();

        TotpSession totpSession = new TotpSession();
        totpSession.setId(UUID.randomUUID());
        totpSession.setTokenHash(tokenHasher.hash(rawToken));
        totpSession.setUserId(user.getId());
        totpSession.setPlatformId(platformId);
        totpSession.setExpiresAt(now.plus(TOTP_SESSION_TTL));
        totpSession.setAttemptCount(0);

        TotpSession saved = totpSessionRepository.save(totpSession);
        log.info("TOTP challenge issued, totpSessionId={}, userId={}, platformId={}",
                saved.getId(), user.getId(), platformId);

        return new LoginResponseDTO(null, rawToken, saved.getExpiresAt());
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
        String[] codes = user.getTotpBackupCodes();
        if (codes == null) {
            return false;
        }

        List<String> remaining = new ArrayList<>(Arrays.asList(codes));
        if (!remaining.remove(code)) {
            return false;
        }

        user.setTotpBackupCodes(remaining.toArray(new String[0]));
        userRepository.save(user);
        return true;
    }
}
