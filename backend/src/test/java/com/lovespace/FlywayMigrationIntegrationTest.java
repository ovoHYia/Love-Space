package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void h2AppliesLatestMigrations() {
        assertEquals("19", jdbc.queryForObject(
                "select version from flyway_schema_history where success = 1 order by installed_rank desc limit 1",
                String.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where lower(table_name) = 'messages' and lower(column_name) = 'version'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where lower(table_name) = 'memories' and lower(column_name) = 'event_time_known'",
                Integer.class));
    }
}
