package com.lovespace.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {
    @Test
    void acceptsLoopbackProxyDeploymentWithExactHttpsOrigin() {
        assertDoesNotThrow(() -> validator(
                "127.0.0.1", true, "https://love.example.com", "framework").validate());
    }

    @Test
    void rejectsPublicBindingInsecureCookiesCorsAndForwarding() {
        assertThrows(IllegalStateException.class,
                () -> validator("0.0.0.0", true, "https://love.example.com", "framework").validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", false, "https://love.example.com", "framework").validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "http://love.example.com", "framework").validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "https://love.example.com/path", "framework").validate());
        assertThrows(IllegalStateException.class,
                () -> validator("127.0.0.1", true, "https://love.example.com", "none").validate());
    }

    private ProductionSecurityValidator validator(String address, boolean secureCookie,
                                                  String origins, String forwardHeaders) {
        return new ProductionSecurityValidator(address, secureCookie, origins, forwardHeaders);
    }
}
