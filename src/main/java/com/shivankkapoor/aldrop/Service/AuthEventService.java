package com.shivankkapoor.aldrop.Service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivankkapoor.aldrop.Data.AuthEvent;
import com.shivankkapoor.aldrop.Data.AuthEventType;
import com.shivankkapoor.aldrop.Repository.AuthEventRepository;

@Service
public class AuthEventService {

    private static final Logger log = LoggerFactory.getLogger(AuthEventService.class);

    @Autowired
    private AuthEventRepository authEventRepository;

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID platformId, UUID userId, String attemptedUsername, AuthEventType eventType,
            String ipAddress, String userAgent) {
        AuthEvent event = new AuthEvent();
        event.setId(UUID.randomUUID());
        event.setPlatformId(platformId);
        event.setUserId(userId);
        event.setAttemptedUsername(attemptedUsername);
        event.setEventType(eventType);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);

        try {
            AuthEvent saved = authEventRepository.save(event);
            log.info("Auth event recorded, id={}, eventType={}, platformId={}, userId={}",
                    saved.getId(), eventType, platformId, userId);
        } catch (Exception e) {
            log.error("Unable to save auth event, eventType={}, platformId={}, userId={}",
                    eventType, platformId, userId, e);
        }
    }
}
