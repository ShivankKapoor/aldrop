package com.shivankkapoor.aldrop.Service;


import java.time.Duration;
import java.util.UUID;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.DTO.Request.CreatePlatformRequestDTO;
import com.shivankkapoor.aldrop.Exception.PlatformNameTakenException;
import com.shivankkapoor.aldrop.Exception.PlatformNotFoundException;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Security.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(30);
    private static final int API_KEY_BYTES = 32;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    public Platform create(CreatePlatformRequestDTO requestDTO){
        if (platformRepository.findByName(requestDTO.getName()).isPresent()) {
            log.warn("Platform creation rejected, name already taken: {}", requestDTO.getName());
            throw new PlatformNameTakenException(requestDTO.getName());
        }

        Platform platform = new Platform();
        platform.setId(UUID.randomUUID());
        platform.setName(requestDTO.getName());
        platform.setApiKey(tokenGenerator.generate(API_KEY_BYTES));
        platform.setSessionTtl(requestDTO.getSessionTtl() != null ? requestDTO.getSessionTtl() : DEFAULT_SESSION_TTL);
        platform.setMaxSessionsPerUser(requestDTO.getMaxSessionsPerUser());
        platform.setTotpAvailable(requestDTO.isTotpAvailable());
        platform.setRequireDeviceBinding(requestDTO.isRequireDeviceBinding());
        platform.setActive(true);

        Platform saved = platformRepository.save(platform);
        log.info("Platform created, id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    public Platform updateActiveStatus(UUID id, boolean isActive){
        Platform platform = platformRepository.findById(id)
                .orElseThrow(() -> new PlatformNotFoundException(id));

        platform.setActive(isActive);

        Platform saved = platformRepository.save(platform);
        log.info("Platform status updated, id={}, isActive={}", saved.getId(), isActive);
        return saved;
    }

}
