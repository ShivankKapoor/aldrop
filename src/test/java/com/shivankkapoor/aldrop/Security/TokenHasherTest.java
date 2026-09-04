package com.shivankkapoor.aldrop.Security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void hashIsDeterministicForSameInput() {
        String first = tokenHasher.hash("some-token");
        String second = tokenHasher.hash("some-token");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentTokensProduceDifferentHashes() {
        String first = tokenHasher.hash("token-one");
        String second = tokenHasher.hash("token-two");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashDoesNotEqualRawToken() {
        String token = "some-token";

        assertThat(tokenHasher.hash(token)).isNotEqualTo(token);
    }

    @Test
    void hashIsHexEncodedSha256Length() {
        String hash = tokenHasher.hash("some-token");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }
}
