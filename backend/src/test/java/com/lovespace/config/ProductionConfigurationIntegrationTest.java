package com.lovespace.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "CORS_ALLOWED_ORIGINS=https://love.example.test")
@ActiveProfiles({"test", "prod"})
class ProductionConfigurationIntegrationTest {
    @Autowired Environment environment;

    @Test
    void productionProfileResolvesToProxyOnlySecuritySettings() {
        assertEquals("127.0.0.1", environment.getProperty("server.address"));
        assertEquals("true", environment.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("framework", environment.getProperty("server.forward-headers-strategy"));
        assertEquals("https://love.example.test", environment.getProperty("app.cors-allowed-origins"));
    }
}
