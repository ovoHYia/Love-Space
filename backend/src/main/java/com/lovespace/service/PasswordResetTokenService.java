package com.lovespace.service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetTokenService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);
    private static final int MINIMUM_TOKEN_BYTES = 32;
    private final byte[] configuredToken;

    public PasswordResetTokenService(@Value("${PASSWORD_RESET_TOKEN:}") String passwordResetToken) {
        configuredToken = passwordResetToken.getBytes(StandardCharsets.UTF_8);
        if (configuredToken.length > 0 && configuredToken.length < MINIMUM_TOKEN_BYTES) {
            throw new IllegalStateException(
                    "PASSWORD_RESET_TOKEN must contain at least 32 bytes of cryptographically random data");
        }
    }

    @PostConstruct
    void logConfiguration() {
        if (!isConfigured()) {
            log.warn("Password recovery is disabled because PASSWORD_RESET_TOKEN is empty");
            return;
        }
        log.info("Password recovery token configured (SHA-256 fingerprint: {})", fingerprint());
    }

    public boolean isConfigured() {
        return configuredToken.length > 0;
    }

    public boolean matches(String suppliedToken) {
        byte[] supplied = suppliedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configuredToken, supplied);
    }

    String fingerprint() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(configuredToken);
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
