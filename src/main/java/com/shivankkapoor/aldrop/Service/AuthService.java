package com.shivankkapoor.aldrop.Service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.Data.Session;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.LoginRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.LoginResponseDTO;
import com.shivankkapoor.aldrop.Exception.InvalidCredentialsException;
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

        String token = tokenGenerator.generate(SESSION_TOKEN_BYTES);
        OffsetDateTime now = OffsetDateTime.now();

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
}
