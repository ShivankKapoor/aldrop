package com.shivankkapoor.aldrop.Data;


import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;

    @Column(name = "platform_id", nullable = false, unique = false)
    private UUID platformId;

    @Column(name= "username", nullable = false, unique = false)
    private String username;

    @Column(name="password_hash",nullable=false, unique=false)
    private String passwordHash;

    @Column(name="totp_enabled",nullable=false, unique=false)
    private boolean totpEnabled; 

    @Column(name="totp_seed",nullable=true, unique=false)
    private String totpSeed;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name="totp_backup_codes",nullable=true, unique=false)
    private String[] totpBackupCodes;

    @Column(name="is_active",nullable=false, unique=false)
    private boolean isActive;

}
