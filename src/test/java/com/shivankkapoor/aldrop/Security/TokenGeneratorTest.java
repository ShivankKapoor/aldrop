package com.shivankkapoor.aldrop.Security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

    private final TokenGenerator tokenGenerator = new TokenGenerator();

    @Test
    void generatesTokenThatDecodesToRequestedByteLength() {
        String token = tokenGenerator.generate(32);

        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32);
    }

    @Test
    void generatesUrlSafeTokenWithoutPadding() {
        String token = tokenGenerator.generate(32);

        assertThat(token).doesNotContain("=", "+", "/");
    }

    @Test
    void generatesDifferentTokensOnSuccessiveCalls() {
        String first = tokenGenerator.generate(32);
        String second = tokenGenerator.generate(32);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void respectsRequestedByteLength() {
        String token = tokenGenerator.generate(16);

        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(16);
    }
}
