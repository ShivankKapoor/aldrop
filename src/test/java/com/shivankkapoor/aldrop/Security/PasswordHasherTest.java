package com.shivankkapoor.aldrop.Security;

import static org.assertj.core.api.Assertions.assertThat;

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
}
