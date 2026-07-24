package com.lovespace.config;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecurityValidator {
    private final String serverAddress;
    private final boolean secureCookie;
    private final String corsAllowedOrigins;
    private final String forwardHeadersStrategy;

    public ProductionSecurityValidator(
            @Value("${server.address}") String serverAddress,
            @Value("${server.servlet.session.cookie.secure}") boolean secureCookie,
            @Value("${app.cors-allowed-origins}") String corsAllowedOrigins,
            @Value("${server.forward-headers-strategy}") String forwardHeadersStrategy) {
        this.serverAddress = serverAddress;
        this.secureCookie = secureCookie;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.forwardHeadersStrategy = forwardHeadersStrategy;
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
