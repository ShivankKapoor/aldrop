package com.shivankkapoor.aldrop.Repository;

import com.shivankkapoor.aldrop.Data.TotpSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TotpSessionRepository extends JpaRepository<TotpSession, UUID> {

    Optional<TotpSession> findByTokenHash(String tokenHash);
}
