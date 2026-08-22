package com.lovespace.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductionDatabaseSecurityValidatorTest {
    private static final String PASSWORD = "test-password";

    @Test
    void allowsExplicitNonTlsForLoopbackDatabase() {
        assertDoesNotThrow(() -> ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                "jdbc:mysql://127.0.0.1:3306/love_space?sslMode=DISABLED", PASSWORD));
    }

    @Test
    void allowsTlsForRemoteDatabase() {
        assertDoesNotThrow(() -> ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                "jdbc:mysql://db.example.internal:3306/love_space?sslMode=VERIFY_IDENTITY", PASSWORD));
    }

    @Test
    void rejectsEmptyDatabasePasswordForMysqlUrl() {
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://127.0.0.1:3306/love_space?sslMode=DISABLED", ""));
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://127.0.0.1:3306/love_space?sslMode=DISABLED", "   "));
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://127.0.0.1:3306/love_space?sslMode=DISABLED", null));
    }

    @Test
    void skipsPasswordCheckForNonMysqlDatasource() {
        assertDoesNotThrow(() -> ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                "jdbc:h2:mem:love_space", ""));
    }

    @Test
    void rejectsRemoteNonTlsAndAmbiguousConnections() {
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://db.example.internal:3306/love_space?sslMode=DISABLED", PASSWORD));
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://db.example.internal:3306/love_space?sslMode=PREFERRED", PASSWORD));
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://db.example.internal:3306/love_space", PASSWORD));
    }
}
