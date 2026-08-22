package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void h2AppliesLatestMigrations() throws Exception {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql");
        Pattern namePattern = Pattern.compile("V(\\d+)__");
        int latest = 0;
        for (Resource migration : migrations) {
            Matcher matcher = namePattern.matcher(String.valueOf(migration.getFilename()));
            if (matcher.find()) {
                latest = Math.max(latest, Integer.parseInt(matcher.group(1)));
            }
        }
        assertTrue(latest > 0, "should discover at least one migration");
        assertEquals(String.valueOf(latest), jdbc.queryForObject(
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
