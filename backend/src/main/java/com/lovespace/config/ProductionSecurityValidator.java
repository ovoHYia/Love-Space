package com.lovespace.config;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecurityValidator {
    private static final int MINIMUM_SETUP_TOKEN_BYTES = 32;
    private static final Set<String> SETUP_TOKEN_PLACEHOLDERS = Set.of(
            "change-me", "change_me", "changeme", "replace-me", "replace_me",
            "replace-with-a-random-64-character-token", "your-token", "your_setup_token",
            "your-setup-token", "setup-token", "setup_token", "test", "test-token",
            "test-setup-token", "password", "secret", "default", "example", "example-token");
    private final String serverAddress;
    private final boolean secureCookie;
    private final String corsAllowedOrigins;
    private final String forwardHeadersStrategy;
    private final String setupToken;
    private final boolean setupEnabled;

    public ProductionSecurityValidator(
            @Value("${server.address}") String serverAddress,
            @Value("${server.servlet.session.cookie.secure}") boolean secureCookie,
            @Value("${app.cors-allowed-origins}") String corsAllowedOrigins,
            @Value("${server.forward-headers-strategy}") String forwardHeadersStrategy,
            @Value("${SETUP_TOKEN:}") String setupToken,
            @Value("${app.security.setup.enabled:true}") boolean setupEnabled) {
        this.serverAddress = serverAddress;
        this.secureCookie = secureCookie;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.forwardHeadersStrategy = forwardHeadersStrategy;
        this.setupToken = setupToken == null ? "" : setupToken;
        this.setupEnabled = setupEnabled;
    }

    @PostConstruct
    void validate() {
        if (!isLoopback(serverAddress)) {
            throw new IllegalStateException(
                    "Production server.address must be a loopback address behind a reverse proxy");
        }
        if (!secureCookie) {
            throw new IllegalStateException("Production session cookies must be secure");
        }
        if (!"framework".equalsIgnoreCase(forwardHeadersStrategy)) {
            throw new IllegalStateException(
                    "Production server.forward-headers-strategy must be framework");
        }
        String[] origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
        if (origins.length == 0 || Arrays.stream(origins).anyMatch(origin -> !isExactHttpsOrigin(origin))) {
            throw new IllegalStateException(
                    "Production CORS_ALLOWED_ORIGINS must contain only exact HTTPS origins");
        }
        validateSetupToken();
    }

    private void validateSetupToken() {
        if (!setupEnabled) return;
        int bytes = setupToken.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MINIMUM_SETUP_TOKEN_BYTES) {
            throw new IllegalStateException("Production SETUP_TOKEN must contain at least 32 UTF-8 bytes");
        }
        String normalized = setupToken.trim().toLowerCase(Locale.ROOT);
        if (SETUP_TOKEN_PLACEHOLDERS.contains(normalized)
                || normalized.matches("(?:change|replace|your|example|default|test)[-_ ]?(?:me|token|secret|password)?")
                || normalized.chars().distinct().count() == 1) {
            throw new IllegalStateException("Production SETUP_TOKEN must not be a common placeholder value");
        }
    }

    private boolean isLoopback(String value) {
        try {
            return InetAddress.getByName(value).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private boolean isExactHttpsOrigin(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
