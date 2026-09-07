package com.shivankkapoor.aldrop.Security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesAndMatchesCorrectPassword() {
        String hash = passwordHasher.hash("correcthorsebattery");

        assertThat(hash).isNotEqualTo("correcthorsebattery");
        assertThat(passwordHasher.matches("correcthorsebattery", hash)).isTrue();
    }

    @Test
    void rejectsIncorrectPassword() {
        String hash = passwordHasher.hash("correcthorsebattery");

        assertThat(passwordHasher.matches("wrongpassword", hash)).isFalse();
    }

    @Test
    void rejectsAgainstNullHashInsteadOfThrowing() {
        assertThat(passwordHasher.matches("whatever", null)).isFalse();
    }

    @Test
    void concurrentVerificationsAllComplete() throws Exception {
        String hash = passwordHasher.hash("correcthorsebattery");
        ExecutorService pool = Executors.newFixedThreadPool(40);
        try {
            var futures = IntStream.range(0, 40)
                    .<Future<Boolean>>mapToObj(i -> pool.submit(() -> passwordHasher.matches("correcthorsebattery", hash)))
                    .toList();
            for (Future<Boolean> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
