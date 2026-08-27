package com.shivankkapoor.aldrop.Repository;


import com.shivankkapoor.aldrop.Data.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformRepository extends JpaRepository<Platform, UUID> {

    Optional<Platform> findByApiKey(String apiKey);

    Optional<Platform> findByName(String name);
}
