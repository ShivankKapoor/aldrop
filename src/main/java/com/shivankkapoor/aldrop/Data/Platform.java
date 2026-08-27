package com.shivankkapoor.aldrop.Data;

import java.time.Duration;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "platforms")
@NoArgsConstructor
@AllArgsConstructor
public class Platform {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "session_ttl", nullable = false, unique = false)
    private Duration sessionTtl;

    @Column(name="max_sessions_per_user", nullable = true, unique = false)
    private Integer maxSessionsPerUser;

    @Column(name="totp_available",nullable = false, unique = false)
    private boolean totpAvailable;

    @Column(name="is_active",nullable = false, unique = false)
    private boolean isActive;
}
