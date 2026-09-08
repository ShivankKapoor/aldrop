package com.shivankkapoor.aldrop.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivankkapoor.aldrop.DTO.Response.LoginResponseDTO;
import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Data.Session;
import com.shivankkapoor.aldrop.Data.TotpSession;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.Exception.DeviceBindingRequiredException;
import com.shivankkapoor.aldrop.Repository.SessionRepository;
import com.shivankkapoor.aldrop.Repository.TotpSessionRepository;
import com.shivankkapoor.aldrop.Security.TokenGenerator;
import com.shivankkapoor.aldrop.Security.TokenHasher;


@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final int SESSION_TOKEN_BYTES = 32;
    private static final Duration TOTP_SESSION_TTL = Duration.ofMinutes(5);

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TotpSessionRepository totpSessionRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private TokenHasher tokenHasher;

    @Transactional
    public LoginResponseDTO createSessionResponse(User user, Platform platform, UUID platformId,
            String ipAddress, String userAgent) {
        if (platform.isRequireDeviceBinding() && (isBlank(ipAddress) || isBlank(userAgent))) {
            throw new DeviceBindingRequiredException();
        }

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
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);

        Session saved = sessionRepository.save(session);
        log.info("Session created, sessionId={}, userId={}, platformId={}", saved.getId(), user.getId(), platformId);

        return new LoginResponseDTO(rawToken, null, saved.getExpiresAt());
    }

    @Transactional
    public LoginResponseDTO issueTotpChallenge(User user, UUID platformId) {
        int invalidated = totpSessionRepository.deleteUnconsumedByUserIdAndPlatformId(user.getId(), platformId);
        if (invalidated > 0) {
            log.info("Invalidated {} outstanding TOTP challenge(s), userId={}, platformId={}",
                    invalidated, user.getId(), platformId);
        }

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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
