package com.lovespace.config;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionDatabaseSecurityValidator {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://(?<host>\\[[^]]+\\]|[^/:?#]+)(?::(?<port>[0-9]{1,5}))?/(?<database>[A-Za-z0-9_]+)(?:\\?(?<query>.*))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_SSL_MODES = Set.of(
            "DISABLED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");
    private static final Set<String> TLS_SSL_MODES = Set.of(
            "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");
    private final String jdbcUrl;

    public ProductionDatabaseSecurityValidator(@Value("${spring.datasource.url}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @PostConstruct
    void validate() {
        validateJdbcUrlSecurity(jdbcUrl);
    }

    static void validateJdbcUrlSecurity(String value) {
        if (value == null || value.isBlank()
                || !value.regionMatches(true, 0, "jdbc:mysql://", 0, "jdbc:mysql://".length())) {
            // Tests may deliberately replace the production MySQL datasource with H2. The
            // real production application.yml always resolves this property to MySQL.
            return;
        }

        Matcher matcher = MYSQL_URL.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalStateException("Production DB_URL must be a valid MySQL JDBC URL");
        }

        String host = matcher.group("host");
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        Map<String, String> query = parseQuery(matcher.group("query"));
        String sslMode = query.get("sslmode");
        if (sslMode == null || sslMode.isBlank()) {
            String useSsl = query.get("usessl");
            if (useSsl != null) {
                sslMode = "false".equalsIgnoreCase(useSsl) || "0".equals(useSsl)
                        ? "DISABLED" : "REQUIRED";
            }
        }
        sslMode = sslMode == null ? "" : sslMode.toUpperCase(Locale.ROOT);
        if (!ALLOWED_SSL_MODES.contains(sslMode)) {
            throw new IllegalStateException(
                    "Production DB_URL must explicitly set sslMode=DISABLED for loopback or a TLS mode for remote databases");
        }
        if (!isLoopback(host) && !TLS_SSL_MODES.contains(sslMode)) {
            throw new IllegalStateException(
                    "Production remote database connections must require TLS");
        }
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Map.of();
        return Arrays.stream(query.split("&"))
                .filter(part -> !part.isBlank())
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]).toLowerCase(Locale.ROOT),
                        pair -> pair.length > 1 ? decode(pair[1]) : "",
                        (first, ignored) -> first));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isLoopback(String host) {
        if ("localhost".equalsIgnoreCase(host) || "localhost.localdomain".equalsIgnoreCase(host)) {
            return true;
        }
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
