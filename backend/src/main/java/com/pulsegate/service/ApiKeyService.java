package com.pulsegate.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class ApiKeyService {

    private static final String KEY_PREFIX = "pg_";
    private final SecureRandom random = new SecureRandom();

    /** 24 random bytes = 192 bits of entropy, URL-safe. */
    public String generate() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String displayPrefix(String apiKey) {
        return apiKey.substring(0, 8);
    }
}
