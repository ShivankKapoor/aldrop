package com.shivankkapoor.aldrop.Service;

import java.util.Locale;
import java.util.UUID;

import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.RegisterUserRequestDTO;
import com.shivankkapoor.aldrop.Exception.UsernameTakenException;
import com.shivankkapoor.aldrop.Repository.UserRepository;
import com.shivankkapoor.aldrop.Security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    public User register(UUID platformId, RegisterUserRequestDTO requestDTO){
        String username = requestDTO.getUsername().toLowerCase(Locale.ROOT);

        if (userRepository.findByPlatformIdAndUsername(platformId, username).isPresent()) {
            log.warn("User registration rejected, username already taken: platformId={}, username={}", platformId, username);
            throw new UsernameTakenException(username);
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPlatformId(platformId);
        user.setUsername(username);
        user.setPasswordHash(passwordHasher.hash(requestDTO.getPassword()));
        user.setTotpEnabled(false);
        user.setActive(true);

        User saved = userRepository.save(user);
        log.info("User registered, id={}, platformId={}, username={}", saved.getId(), saved.getPlatformId(), saved.getUsername());
        return saved;
    }
}
