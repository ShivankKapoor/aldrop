package com.shivankkapoor.aldrop.Data;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "sessions")
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    
    @Id
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, unique = false)
    private UUID userId;

    @Column(name = "platform_id", nullable = false, unique = false)
    private UUID platformId;

    @Column(name = "created_at", nullable = false, unique = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false, unique = false)
    private OffsetDateTime expiresAt;

    @UpdateTimestamp
    @Column(name = "last_used_at", nullable = false, unique = false)
    private OffsetDateTime lastUsedAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", nullable = true, unique = false)
    private String ipAddress;

    @Column(name = "user_agent", nullable = true, unique = false)
    private String userAgent;
}
