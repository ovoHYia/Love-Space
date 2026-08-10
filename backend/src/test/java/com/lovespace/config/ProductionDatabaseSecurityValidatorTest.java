package com.lovespace.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductionDatabaseSecurityValidatorTest {
    @Test
    void allowsExplicitNonTlsForLoopbackDatabase() {
        assertDoesNotThrow(() -> ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                "jdbc:mysql://127.0.0.1:3306/love_space?sslMode=DISABLED"));
    }

    @Test
    void allowsTlsForRemoteDatabase() {
        assertDoesNotThrow(() -> ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                "jdbc:mysql://db.example.internal:3306/love_space?sslMode=VERIFY_IDENTITY"));
    }

    @Test
    void rejectsRemoteNonTlsAndAmbiguousConnections() {
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://db.example.internal:3306/love_space?sslMode=DISABLED"));
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://db.example.internal:3306/love_space?sslMode=PREFERRED"));
        assertThrows(IllegalStateException.class, () ->
                ProductionDatabaseSecurityValidator.validateJdbcUrlSecurity(
                        "jdbc:mysql://db.example.internal:3306/love_space"));
    }
}
