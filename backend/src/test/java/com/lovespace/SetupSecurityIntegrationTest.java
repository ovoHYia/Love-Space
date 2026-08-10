package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupSecurityIntegrationTest {
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
    void initializationIsLimitedByIpAndRepeatedInitializationIsAConflict() throws Exception {
        String setup = setupBody("alice", "alice-pass-123", "bob", "bob-pass-123");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/setup/initialize").with(csrf())
                            .header("X-Setup-Token", "wrong-token")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.10");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON).content(setup))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INVALID_SETUP_TOKEN"));
        }
        mvc.perform(post("/api/setup/initialize").with(csrf())
                        .header("X-Setup-Token", "wrong-token")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SETUP_RATE_LIMITED"));

        mvc.perform(post("/api/setup/initialize").with(csrf()).header("X-Setup-Token", setupToken)
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.11");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isCreated());

        String repeated = mvc.perform(post("/api/setup/initialize").with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.12");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_INITIALIZED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(repeated.contains("alice"));
        assertFalse(repeated.contains("alice-pass-123"));
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
