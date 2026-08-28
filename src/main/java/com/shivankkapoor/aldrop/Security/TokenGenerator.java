package com.shivankkapoor.aldrop.Security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class TokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate(int numBytes) {
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
