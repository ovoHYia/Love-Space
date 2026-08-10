package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class MySqlFlywayIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
    void allMigrationsApplyToRealMySql() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        assertTrue(url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/[A-Za-z0-9_]*_test(?:\\?.*)?"),
                "MYSQL_TEST_URL must target a local database whose name ends with _test");
        String username = System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        flyway.validate();

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.prepareStatement(
                     "select version from flyway_schema_history where success = 1 order by installed_rank desc limit 1");
             var result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals("14", result.getString(1));
        }
    }
}
