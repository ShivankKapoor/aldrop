package com.shivankkapoor.aldrop.Repository;

import com.shivankkapoor.aldrop.Data.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPlatformIdAndUsername(UUID platformId, String username);

    @Modifying
    @Query(value = "UPDATE users SET totp_backup_codes = array_remove(totp_backup_codes, :code) "
            + "WHERE id = :userId AND :code = ANY(totp_backup_codes)", nativeQuery = true)
    int consumeBackupCodeIfPresent(@Param("userId") UUID userId, @Param("code") String code);
}
