package com.shivankkapoor.aldrop.Repository;

import com.shivankkapoor.aldrop.Data.TotpSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TotpSessionRepository extends JpaRepository<TotpSession, UUID> {

    Optional<TotpSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM TotpSession t WHERE t.expiresAt < :now")
    int deleteAllByExpiresAtBefore(OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM TotpSession t WHERE t.userId = :userId AND t.platformId = :platformId AND t.consumedAt IS NULL")
    int deleteUnconsumedByUserIdAndPlatformId(UUID userId, UUID platformId);
}
