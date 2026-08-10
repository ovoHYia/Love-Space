package com.lovespace;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        registry.add("spring.datasource.username", () -> mysqlConfigured()
                ? environment("MYSQL_TEST_USERNAME", "root") : "sa");
        registry.add("spring.datasource.password", () -> mysqlConfigured()
                ? requiredEnvironment("MYSQL_TEST_PASSWORD") : "");
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
    void springJpaBusinessFlowWorksAgainstRealMySql() throws Exception {
        requireMySqlConfiguration();
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
                ("{\"title\":\"MySQL 回忆\",\"description\":\"MySQL 描述\","
                        + "\"eventAt\":\"2026-08-10T18:30:00\",\"location\":\"MySQL 地点\","
                        + "\"tags\":[\"MySQL Tag\"]}").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile image = new MockMultipartFile("files", "mysql.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        String firstMemory = mvc.perform(multipart("/api/memories").file(data).file(image)
                        .with(csrf()).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("MySQL 回忆"))
                .andExpect(jsonPath("$.media[0].mediaType").value("image"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long firstMemoryId = idOf(firstMemory);

        MockMultipartFile video = new MockMultipartFile("files", "mysql.mp4", "video/mp4",
                new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0,
                        'i', 's', 'o', 'm', 'm', 'p', '4', '2'});
        mvc.perform(multipart("/api/memories/{id}/media", firstMemoryId).file(video)
                        .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[?(@.mediaType == 'video')]").isNotEmpty());

        MockMultipartFile olderData = new MockMultipartFile("data", "", "application/json",
                ("{\"title\":\"MySQL 旧回忆\",\"description\":\"旧描述\","
                        + "\"eventAt\":\"2026-08-09T18:30:00\",\"location\":\"旧地点\","
                        + "\"tags\":[\"其他\"]}").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile olderImage = new MockMultipartFile("files", "mysql-old.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        mvc.perform(multipart("/api/memories").file(olderData).file(olderImage)
                        .with(csrf()).session(session))
                .andExpect(status().isCreated());

        assertEquals(1, jdbc.queryForObject("select count(*) from couples", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from users", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from diaries", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from memories", Integer.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from media", Integer.class));
        try (var paths = Files.list(uploadDir)) {
            assertEquals(3, paths.count());
        }

        mvc.perform(get("/api/memories/album").session(session)
                        .param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].memoryTitle").value("MySQL 回忆"))
                .andExpect(jsonPath("$.content[0].media.mediaType").value("video"))
                .andExpect(jsonPath("$.content[1].media.mediaType").value("image"))
                .andExpect(jsonPath("$.content[2].memoryTitle").value("MySQL 旧回忆"));
        mvc.perform(get("/api/memories/album").session(session)
                        .param("q", "MySQL 地点").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/memories/album").session(session)
                        .param("tag", " mysql   tag ").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/memories/album").session(session)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].memoryTitle").value("MySQL 旧回忆"));
    }

    @Test
    void albumExplainPlansAreAvailableAgainstRealMySql() {
        requireMySqlConfiguration();
        assertIndex("memories", "idx_memories_couple_event");
        assertIndex("media", "idx_media_couple");
        assertMemoryTagsPrimaryKey();

        String noFilter = String.format(ALBUM_EXPLAIN_BASE, "");
        String tagFilter = String.format(ALBUM_EXPLAIN_BASE,
                "and exists (select 1 from memory_tags mt where mt.memory_id = mem.id and lower(mt.tag) = ?)");
        String keywordFilter = String.format(ALBUM_EXPLAIN_BASE,
                "and (lower(mem.title) like ? or lower(mem.description) like ? or lower(mem.location) like ?)");

        List<Map<String, Object>> noFilterPlan = jdbc.queryForList("explain " + noFilter, 0L, 0L);
        List<Map<String, Object>> tagPlan = jdbc.queryForList("explain " + tagFilter, 0L, 0L, "mysql tag");
        List<Map<String, Object>> keywordPlan = jdbc.queryForList(
                "explain " + keywordFilter, 0L, 0L, "%mysql%", "%mysql%", "%mysql%");
        assertFalse(noFilterPlan.isEmpty());
        assertFalse(tagPlan.isEmpty());
        assertFalse(keywordPlan.isEmpty());
        System.out.println("MySQL EXPLAIN album no-filter: " + noFilterPlan);
        System.out.println("MySQL EXPLAIN album tag-filter: " + tagPlan);
        System.out.println("MySQL EXPLAIN album keyword-filter: " + keywordPlan);
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
        String url = System.getenv("MYSQL_TEST_URL");
        String password = System.getenv("MYSQL_TEST_PASSWORD");
        return url != null && !url.isBlank()
                && password != null && !password.isBlank()
                && isLocalTestDatabase(url);
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
        if (!isLocalTestDatabase(url)) {
            throw new IllegalStateException("MYSQL_TEST_URL must target a local database whose name ends with _test");
        }
    }

    private static boolean isLocalTestDatabase(String url) {
        return url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/[A-Za-z0-9_]*_test(?:\\?.*)?");
    }

    private static void requireMySqlConfiguration() {
        String message = "BLOCKED: 真实 MySQL 测试需要 MYSQL_TEST_URL（本机 *_test 数据库）和非空 MYSQL_TEST_PASSWORD；"
                + "当前未提供安全测试库配置，未使用生产 .env 密码。";
        if (!mysqlConfigured()) System.err.println(message);
        Assumptions.assumeTrue(mysqlConfigured(), message);
        assertTrueLocalTestDatabase();
    }

    private void assertIndex(String table, String index) {
        Integer count = jdbc.queryForObject(
                "select count(distinct index_name) from information_schema.statistics "
                        + "where table_schema = database() and table_name = ? and index_name = ?",
                Integer.class, table, index);
        assertEquals(1, count);
    }

    private void assertMemoryTagsPrimaryKey() {
        Integer memoryIdFirst = jdbc.queryForObject(
                "select count(*) from information_schema.statistics "
                        + "where table_schema = database() and table_name = 'memory_tags' "
                        + "and index_name = 'PRIMARY' and column_name = 'memory_id' and seq_in_index = 1",
                Integer.class);
        Integer tagSecond = jdbc.queryForObject(
                "select count(*) from information_schema.statistics "
                        + "where table_schema = database() and table_name = 'memory_tags' "
                        + "and index_name = 'PRIMARY' and column_name = 'tag' and seq_in_index = 2",
                Integer.class);
        assertEquals(1, memoryIdFirst);
        assertEquals(1, tagSecond);
    }

    private static long idOf(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"id\\\":(\\d+)")
                .matcher(json);
        if (!matcher.find()) throw new AssertionError("response has no id: " + json);
        return Long.parseLong(matcher.group(1));
    }

    private static final String ALBUM_EXPLAIN_BASE = """
            select m.id
            from media m
            join memories mem on mem.id = m.memory_id
            where m.couple_id = ?
              and (lower(m.media_type) = 'image' or lower(m.media_type) = 'video')
              and mem.couple_id = ?
              and mem.deleted_at is null
              %s
            order by mem.event_at desc, m.id desc
            limit 30 offset 0
            """;
}
