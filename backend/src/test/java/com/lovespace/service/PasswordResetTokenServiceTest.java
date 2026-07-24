package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordResetTokenServiceTest {
    private static final String SECURE_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void rejectsConfiguredTokenShorterThan256Bits() {
        assertThrows(IllegalStateException.class, () -> new PasswordResetTokenService("too-short"));
    }

    @Test
    void acceptsEmptyOrSufficientlyLongTokens() {
        PasswordResetTokenService disabled = new PasswordResetTokenService("");
        PasswordResetTokenService enabled = new PasswordResetTokenService(SECURE_TOKEN);

        assertFalse(disabled.isConfigured());
        assertTrue(enabled.isConfigured());
        assertTrue(enabled.matches(SECURE_TOKEN));
        assertFalse(enabled.matches("wrong-token"));
    }
}
