package com.shivankkapoor.aldrop.Repository;

import com.shivankkapoor.aldrop.Data.Session;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByTokenHash(String tokenHash);
    List<Session> findByUserId(UUID userId);
    List<Session> findByUserIdAndPlatformId(UUID userId, UUID platformId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Session> findByUserIdAndPlatformIdAndExpiresAtAfterOrderByCreatedAtAsc(UUID userId, UUID platformId, OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM Session s WHERE s.expiresAt < :now")
    int deleteAllByExpiresAtBefore(OffsetDateTime now);
}
