package com.lovespace.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {
    @Test
    void acceptsLoopbackProxyDeploymentWithExactHttpsOrigin() {
        assertDoesNotThrow(() -> validator(
                "127.0.0.1", true, "https://love.example.com", "framework", secureToken(), true).validate());
    }

    @Test
    void rejectsPublicBindingInsecureCookiesCorsAndForwarding() {
        assertThrows(IllegalStateException.class,
                () -> validator("0.0.0.0", true, "https://love.example.com", "framework", secureToken(), true).validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", false, "https://love.example.com", "framework", secureToken(), true).validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "http://love.example.com", "framework", secureToken(), true).validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "https://love.example.com/path", "framework", secureToken(), true).validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "https://love.example.com", "none", secureToken(), true).validate());
    }

    @Test
    void rejectsShortUtf8SetupTokenAtProductionStartup() {
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "https://love.example.com", "framework", "短口令", true).validate());
    }

    @Test
    void rejectsCommonSetupTokenPlaceholdersAndAllowsExplicitDisable() {
        String longPlaceholder = "replace-with-a-random-64-character-token";
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "https://love.example.com", "framework",
                        longPlaceholder, true).validate());
        assertDoesNotThrow(() -> validator(
                "127.0.0.1", true, "https://love.example.com", "framework", "", false).validate());
    }

    private ProductionSecurityValidator validator(String address, boolean secureCookie,
                                                  String origins, String forwardHeaders,
                                                  String setupToken, boolean setupEnabled) {
        return new ProductionSecurityValidator(
                address, secureCookie, origins, forwardHeaders, setupToken, setupEnabled);
    }

    private String secureToken() {
        return "7f4c8d2a9b1e6f305c7a4d8e2b9f1036a5c8d1e7f0b4a6c2d9e5f8a1b3c7d0e4";
    }
}
