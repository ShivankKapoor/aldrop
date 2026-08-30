package com.shivankkapoor.aldrop.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Data.Session;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.LoginRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LogoutRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.ValidateSessionRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.LoginResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.ValidateSessionResponseDTO;
import com.shivankkapoor.aldrop.Exception.InvalidCredentialsException;
import com.shivankkapoor.aldrop.Exception.InvalidSessionException;
import com.shivankkapoor.aldrop.Exception.PlatformNotFoundException;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Repository.SessionRepository;
import com.shivankkapoor.aldrop.Repository.UserRepository;
import com.shivankkapoor.aldrop.Security.PasswordHasher;
import com.shivankkapoor.aldrop.Security.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int SESSION_TOKEN_BYTES = 32;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private SessionRepository sessionRepository;

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

        String token = tokenGenerator.generate(SESSION_TOKEN_BYTES);

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setTokenHash(token);
        session.setUserId(user.getId());
        session.setPlatformId(platformId);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(platform.getSessionTtl()));

        Session saved = sessionRepository.save(session);
        log.info("Login succeeded, sessionId={}, userId={}, platformId={}", saved.getId(), user.getId(), platformId);

        return new LoginResponseDTO(saved.getTokenHash(), saved.getExpiresAt());
    }

    public ValidateSessionResponseDTO validate(UUID platformId, ValidateSessionRequestDTO requestDTO) {
        Session session = sessionRepository.findByTokenHash(requestDTO.getToken())
                .orElseThrow(InvalidSessionException::new);

        if (!session.getPlatformId().equals(platformId) || session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("Session validation rejected, platformId={}, sessionId={}", platformId, session.getId());
            throw new InvalidSessionException();
        }

        Session saved = sessionRepository.save(session);
        log.info("Session validated, sessionId={}, userId={}, platformId={}", saved.getId(), saved.getUserId(), platformId);

        return new ValidateSessionResponseDTO(saved.getUserId(), saved.getExpiresAt());
    }

    public void logout(UUID platformId, LogoutRequestDTO requestDTO) {
        sessionRepository.findByTokenHash(requestDTO.getToken())
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
}
