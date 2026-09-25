package com.shivankkapoor.aldrop.Cache;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.shivankkapoor.aldrop.Data.Session;

public record CachedSession(UUID id, UUID userId, UUID platformId, OffsetDateTime expiresAt, String ipAddress,
        String userAgent) {

    public static CachedSession from(Session session) {
        return new CachedSession(session.getId(), session.getUserId(), session.getPlatformId(),
                session.getExpiresAt(), session.getIpAddress(), session.getUserAgent());
    }
}
