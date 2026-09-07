package com.shivankkapoor.aldrop.Service;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivankkapoor.aldrop.Repository.SessionRepository;
import com.shivankkapoor.aldrop.Repository.TotpSessionRepository;

@Service
public class SessionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TotpSessionRepository totpSessionRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredSessions() {
        OffsetDateTime now = OffsetDateTime.now();
        int sessionsDeleted = sessionRepository.deleteAllByExpiresAtBefore(now);
        int totpSessionsDeleted = totpSessionRepository.deleteAllByExpiresAtBefore(now);
        log.info("Expired session cleanup ran, sessionsDeleted={}, totpSessionsDeleted={}",
                sessionsDeleted, totpSessionsDeleted);
    }
}
