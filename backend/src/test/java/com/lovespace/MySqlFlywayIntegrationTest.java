package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MySqlFlywayIntegrationTest {
    @Test
    void allMigrationsApplyToRealMySql() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        String password = System.getenv("MYSQL_TEST_PASSWORD");
        String message = "BLOCKED: Flyway 真实 MySQL 测试需要 MYSQL_TEST_URL（本机 *_test 数据库）和非空 MYSQL_TEST_PASSWORD；"
                + "当前未提供安全测试库配置，未使用生产 .env 密码。";
        if (url == null || url.isBlank() || password == null || password.isBlank()
                || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/[A-Za-z0-9_]*_test(?:\\?.*)?")) {
            System.err.println(message);
            Assumptions.assumeTrue(false, message);
        }
        assertTrue(url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/[A-Za-z0-9_]*_test(?:\\?.*)?"),
                "MYSQL_TEST_URL must target a local database whose name ends with _test");
        String username = System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root");

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
            assertEquals("16", result.getString(1));
        }
    }
}
