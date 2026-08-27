package com.shivankkapoor.aldrop.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "totp_sessions")
@NoArgsConstructor
@AllArgsConstructor
public class TotpSession {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, unique = false)
    private UUID userId;

    @Column(name = "platform_id", nullable = false, unique = false)
    private UUID platformId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false, unique = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at", nullable = true, unique = false)
    private OffsetDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false, unique = false)
    private int attemptCount;
}
