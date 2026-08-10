package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MySqlBusinessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Value("${SETUP_TOKEN}") String setupToken;
    @TempDir static Path uploadDir;

    @DynamicPropertySource
    static void registerMySqlAndUploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlBusinessIntegrationTest::dataSourceUrl);
        registry.add("spring.datasource.username", () -> environment("MYSQL_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> environment("MYSQL_TEST_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> mysqlConfigured()
                ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver");
        registry.add("app.upload-dir", () -> uploadDir.toString());
    }

    @BeforeEach
    void resetDatabase() {
        if (!mysqlConfigured()) return;
        assertTrueLocalTestDatabase();
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @AfterEach
    void restoreForeignKeyChecks() {
        if (mysqlConfigured() && jdbc != null) jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
    void springJpaBusinessFlowWorksAgainstRealMySql() throws Exception {
        String setup = """
                {"spaceName":"真实数据库小屋","loveStartedAt":"2025-02-14T20:00:00",
                 "firstUser":{"username":"alice","password":"alice-pass-123","nickname":"小爱"},
                 "secondUser":{"username":"bob","password":"bob-pass-123","nickname":"小宝"}}
                """;
        mvc.perform(post("/api/setup/initialize").with(csrf()).header("X-Setup-Token", setupToken)
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isCreated());

        MockHttpSession session = login("alice", "alice-pass-123");
        mvc.perform(post("/api/diaries").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"MySQL 日记\",\"content\":\"持久化验证\",\"diaryDate\":\"2026-08-10\"}"))
                .andExpect(status().isCreated());

        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                "{\"title\":\"MySQL 回忆\",\"eventAt\":\"2026-08-10T18:30:00\",\"tags\":[\"真实库\"]}"
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile image = new MockMultipartFile("files", "mysql.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        mvc.perform(multipart("/api/memories").file(data).file(image).with(csrf()).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("MySQL 回忆"))
                .andExpect(jsonPath("$.media[0].mediaType").value("image"));

        assertEquals(1, jdbc.queryForObject("select count(*) from couples", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from users", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from diaries", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from memories", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from media", Integer.class));
        try (var paths = Files.list(uploadDir)) {
            assertEquals(1, paths.count());
        }
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username).param("password", password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private static boolean mysqlConfigured() {
        String value = System.getenv("MYSQL_TEST_URL");
        return value != null && !value.isBlank();
    }

    private static String dataSourceUrl() {
        if (mysqlConfigured()) return requiredEnvironment("MYSQL_TEST_URL");
        return "jdbc:h2:mem:mysql-business-skipped-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    private static String environment(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    private static void assertTrueLocalTestDatabase() {
        String url = requiredEnvironment("MYSQL_TEST_URL");
        if (!url.matches("jdbc:mysql://(127\\.0\\.1|localhost):\\d+/[A-Za-z0-9_]*_test(?:\\?.*)?")) {
            throw new IllegalStateException("MYSQL_TEST_URL must target a local database whose name ends with _test");
        }
    }
}
