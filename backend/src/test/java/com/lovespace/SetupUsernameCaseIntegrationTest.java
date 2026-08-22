package com.lovespace;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupUsernameCaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Value("${SETUP_TOKEN}") String setupToken;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    void usernamesThatDifferOnlyByCaseAreRejected() throws Exception {
        // MySQL 的 utf8mb4_unicode_ci 大小写不敏感，应用层必须先拦截，避免唯一约束在数据库才报错
        mvc.perform(post("/api/setup/initialize").with(csrf()).header("X-Setup-Token", setupToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupBody("Alice", "alice-pass-123", "alice", "bob-pass-123")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/setup/initialize").with(csrf()).header("X-Setup-Token", setupToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupBody("alice", "alice-pass-123", "bob", "bob-pass-123")))
                .andExpect(status().isCreated());
    }

    private String setupBody(String firstUsername, String firstPassword,
                             String secondUsername, String secondPassword) {
        return "{\"spaceName\":\"我们的小时光\",\"loveStartedAt\":\"2025-02-14T20:00:00\","
                + "\"firstUser\":{\"username\":\"" + firstUsername + "\",\"password\":\""
                + firstPassword + "\",\"nickname\":\"小爱\"},"
                + "\"secondUser\":{\"username\":\"" + secondUsername + "\",\"password\":\""
                + secondPassword + "\",\"nickname\":\"小宝\"}}";
    }
}
