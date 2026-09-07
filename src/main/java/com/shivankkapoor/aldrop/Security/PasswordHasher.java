package com.shivankkapoor.aldrop.Security;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    private static final int MAX_CONCURRENT_VERIFICATIONS = 32;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private final String dummyHash = encoder.encode("dummy-password-for-constant-time-login");
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_VERIFICATIONS);

    public String hash(String rawPassword) {
        return withPermit(() -> encoder.encode(rawPassword));
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        return withPermit(() -> encoder.matches(rawPassword, hashedPassword != null ? hashedPassword : dummyHash));
    }

    private <T> T withPermit(Supplier<T> action) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an Argon2 verification slot", e);
        }
        try {
            return action.get();
        } finally {
            semaphore.release();
        }
    }
}
