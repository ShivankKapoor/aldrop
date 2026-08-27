package com.shivankkapoor.aldrop.Repository;

import com.shivankkapoor.aldrop.Data.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPlatformIdAndUsername(UUID platformId, String username);
}
