package com.shivankkapoor.aldrop.Validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import com.shivankkapoor.aldrop.DTO.Request.LoginRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.RegisterUserRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.VerifyTotpRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The caps exist so an oversized value is rejected at the edge rather than by whatever consumes it:
 * an unbounded password reaches Argon2, an unbounded username becomes a rate-limiter cache key.
 */
class FieldLimitsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static String repeat(int length) {
        return "a".repeat(length);
    }

    private static <T> Set<String> invalidFields(T dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void acceptsAPasswordAtTheLimit() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername("alice");
        request.setPassword(repeat(FieldLimits.PASSWORD_MAX));

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void rejectsAPasswordOverTheLimit() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername("alice");
        request.setPassword(repeat(FieldLimits.PASSWORD_MAX + 1));

        assertThat(invalidFields(request)).containsExactly("password");
    }

    @Test
    void rejectsAnOversizedUsername() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername(repeat(FieldLimits.USERNAME_MAX + 1));
        request.setPassword("correcthorse123");

        assertThat(invalidFields(request)).containsExactly("username");
    }

    @Test
    void rejectsAnOversizedLoginPayload() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername(repeat(FieldLimits.USERNAME_MAX + 1));
        request.setPassword(repeat(FieldLimits.PASSWORD_MAX + 1));
        request.setUserAgent(repeat(FieldLimits.USER_AGENT_MAX + 1));

        assertThat(invalidFields(request)).containsExactlyInAnyOrder("username", "password", "userAgent");
    }

    @Test
    void acceptsATypicalLoginPayload() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice@example.com");
        request.setPassword("correcthorse123");
        request.setIpAddress("10.0.0.88");
        request.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void rejectsAnOversizedTokenAndCode() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        request.setTotpToken(repeat(FieldLimits.TOKEN_MAX + 1));
        request.setCode(repeat(FieldLimits.CODE_MAX + 1));

        assertThat(invalidFields(request)).containsExactlyInAnyOrder("totpToken", "code");
    }

    @Test
    void acceptsARealTokenAndCode() {
        VerifyTotpRequestDTO request = new VerifyTotpRequestDTO();
        // a session token is 32 random bytes base64url encoded, so about 43 characters
        request.setTotpToken(repeat(43));
        request.setCode("123456");

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void theLongestAcceptedIpAddressFits() {
        // the longest address IpAddressPattern accepts is a fully expanded IPv6 one, 39 characters.
        // the cap is 45, which leaves room for the IPv4-mapped form (2001:0db8:...:192.168.100.228)
        // that the pattern does not currently match, so widening the pattern would not need the cap
        // raised as well.
        String longest = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        assertThat(longest.length()).isLessThanOrEqualTo(FieldLimits.IP_ADDRESS_MAX);

        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse123");
        request.setIpAddress(longest);

        assertThat(invalidFields(request)).isEmpty();
    }
}
