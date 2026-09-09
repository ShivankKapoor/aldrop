package com.shivankkapoor.aldrop.Data;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "auth_events")
@NoArgsConstructor
@AllArgsConstructor
public class AuthEvent {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;

    @Column(name = "platform_id", nullable = false, unique = false)
    private UUID platformId;

    @Column(name = "user_id", nullable = true, unique = false)
    private UUID userId;

    @Column(name = "attempted_username", nullable = true, unique = false)
    private String attemptedUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, unique = false)
    private AuthEventType eventType;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", nullable = true, unique = false)
    private String ipAddress;

    @Column(name = "user_agent", nullable = true, unique = false)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
