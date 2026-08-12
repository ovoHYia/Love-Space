package com.lovespace;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lovespace.domain.Couple;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.io.TempDir;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @TempDir static Path uploadDir;
    @TempDir static Path exportDir;

    @DynamicPropertySource
    static void registerUploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDir.toString());
        registry.add("app.data-export.dir", () -> exportDir.toString());
    }

    private MockHttpSession alice;
    private MockHttpSession bob;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications", "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories", "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Couple couple = new Couple();
        couple.setSpaceName("我们的小时光");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.save(couple);
        users.save(user(couple, "alice", "小爱", "alice-pass-123"));
        users.save(user(couple, "bob", "小宝", "bob-pass-123"));
        alice = login("alice", "alice-pass-123");
        bob = login("bob", "bob-pass-123");
    }

    @AfterEach
    void removeUploadedFiles() throws Exception {
        clearDirectory(uploadDir);
        clearDirectory(exportDir);
    }

    @Test
    void deletedContentCanBeRestoredAndMemoryCanBePurged() throws Exception {
        long memoryId = createMemory("海边");
        long mediaId = jdbc.queryForObject("select id from media where memory_id = ?", Long.class, memoryId);
        long diaryId = idOf(mvc.perform(post("/api/diaries").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"今天\",\"content\":\"很幸福\",\"diaryDate\":\"2026-07-24\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long messageId = idOf(mvc.perform(post("/api/messages").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"晚安\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long anniversaryId = idOf(mvc.perform(post("/api/anniversaries").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"第一次见面\",\"eventDate\":\"2025-02-14\",\"type\":\"LOVE\",\"recurringYearly\":true,\"reminderDays\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long wishId = idOf(mvc.perform(post("/api/wishes").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"一起看极光\",\"category\":\"TRAVEL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mvc.perform(delete("/api/memories/{id}", memoryId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/diaries/{id}", diaryId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/messages/{id}", messageId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/anniversaries/{id}", anniversaryId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/wishes/{id}", wishId).with(csrf()).session(alice)).andExpect(status().isNoContent());

        mvc.perform(get("/api/trash").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("MEMORY", "DIARY", "MESSAGE", "ANNIVERSARY", "WISH")));
        mvc.perform(get("/api/trash").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/media/{id}", mediaId).session(alice)).andExpect(status().isNotFound());

        restore("DIARY", diaryId);
        restore("MESSAGE", messageId);
        restore("ANNIVERSARY", anniversaryId);
        restore("WISH", wishId);
        restore("MEMORY", memoryId);
        mvc.perform(get("/api/diaries").session(alice)).andExpect(jsonPath("$[0].title").value("今天"));
        mvc.perform(get("/api/messages").session(bob)).andExpect(jsonPath("$.content[0].content").doesNotExist());
        mvc.perform(get("/api/anniversaries").session(alice)).andExpect(jsonPath("$[0].title").value("第一次见面"));
        mvc.perform(get("/api/wishes").session(alice)).andExpect(jsonPath("$[0].title").value("一起看极光"));
        mvc.perform(get("/api/media/{id}", mediaId).session(alice)).andExpect(status().isOk());

        mvc.perform(delete("/api/memories/{id}", memoryId).with(csrf()).session(alice))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/trash/MEMORY/{id}", memoryId).with(csrf()).session(alice))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from memories where id = ?", Integer.class, memoryId));
        assertEquals(0, jdbc.queryForObject("select count(*) from media where id = ?", Integer.class, mediaId));
        mvc.perform(get("/api/trash").session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void exportContainsJsonAndMediaWithoutFutureLettersOrPasswords() throws Exception {
        createMemory("海边");
        mvc.perform(post("/api/diaries").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"夏日记录\",\"content\":\"一起散步\",\"diaryDate\":\"2026-07-24\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/calendar/events").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"周末约会","startAt":"2026-07-26T15:00:00",
                                 "allDay":false,"category":"DATE"}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/messages").with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"未来秘密\",\"deliverAt\":\"2029-01-01T08:00:00\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/games").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"DRAW_GUESS\"}"))
                .andExpect(status().isCreated());

        MvcResult started = mvc.perform(get("/api/data/export").session(alice))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult completed = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("love-space-export-")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        Map<String, byte[]> entries = unzip(completed.getResponse().getContentAsByteArray());
        assertTrue(entries.containsKey("love-space-data.json"));
        assertTrue(entries.keySet().stream().anyMatch(name -> name.matches("media/\\d+-sea\\.png")));
        String json = new String(entries.get("love-space-data.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"formatVersion\":3"));
        assertTrue(json.contains("海边"));
        assertTrue(json.contains("夏日记录"));
        assertTrue(json.contains("周末约会"));
        assertTrue(json.contains("\"calendarEvents\""));
        assertTrue(json.contains("\"games\""));
        assertTrue(json.contains("DRAW_GUESS"));
        assertFalse(json.contains("奶茶"));
        assertFalse(json.contains("未来秘密"));
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("alice-pass-123"));
    }

    @Test
    void exportFailsBeforeStreamingWhenAStoredMediaFileIsMissing() throws Exception {
        long memoryId = createMemory("缺失文件检查");
        String storedName = jdbc.queryForObject(
                "select stored_name from media where memory_id = ?", String.class, memoryId);
        Files.delete(uploadDir.resolve(storedName));

        mvc.perform(get("/api/data/export").session(alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("媒体原文件缺失")));
        assertNoTemporaryExports();
    }

    @Test
    void preparedExportSurvivesMediaDeletionIsOneTimeAndCleansSnapshot() throws Exception {
        long memoryId = createMemory("快照删除竞态");
        long mediaId = jdbc.queryForObject("select id from media where memory_id = ?", Long.class, memoryId);
        String preparation = mvc.perform(post("/api/data/export/prepare").with(csrf()).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").isString())
                .andExpect(jsonPath("$.expiresAt").value(containsString("+08:00")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String downloadPath = downloadPath(preparation);

        mvc.perform(delete("/api/memories/{id}/media/{mediaId}", memoryId, mediaId)
                        .with(csrf()).session(alice))
                .andExpect(status().isOk());

        MvcResult started = mvc.perform(get("/api" + downloadPath).session(alice))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult completed = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn();
        Map<String, byte[]> entries = unzip(completed.getResponse().getContentAsByteArray());
        assertTrue(entries.keySet().stream().anyMatch(name -> name.matches("media/\\d+-sea\\.png")));
        assertNoTemporaryExports();

        mvc.perform(get("/api" + downloadPath).session(alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void preparingAgainReplacesTheSameUsersPendingSnapshot() throws Exception {
        String first = mvc.perform(post("/api/data/export/prepare").with(csrf()).session(alice))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String firstPath = downloadPath(first);
        String second = mvc.perform(post("/api/data/export/prepare").with(csrf()).session(alice))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String secondPath = downloadPath(second);

        mvc.perform(get("/api" + firstPath).session(alice)).andExpect(status().isNotFound());
        MvcResult started = mvc.perform(get("/api" + secondPath).session(alice))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        assertNoTemporaryExports();
    }

    private void restore(String type, long id) throws Exception {
        mvc.perform(post("/api/trash/{type}/{id}/restore", type, id).with(csrf()).session(alice))
                .andExpect(status().isNoContent());
    }

    private long createMemory(String title) throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                ("{\"title\":\"" + title + "\",\"eventAt\":\"2026-07-01T18:30:00\"}")
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "sea.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        String response = mvc.perform(multipart("/api/memories").file(data).file(file).with(csrf()).session(alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return idOf(response);
    }

    private User user(Couple couple, String username, String nickname, String password) {
        User value = new User();
        value.setCouple(couple);
        value.setUsername(username);
        value.setNickname(nickname);
        value.setPasswordHash(encoder.encode(password));
        return value;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username).param("password", password))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private long idOf(String json) {
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(matcher.find(), "响应中缺少 id");
        return Long.parseLong(matcher.group(1));
    }

    private String downloadPath(String json) {
        Matcher matcher = Pattern.compile("\\\"downloadUrl\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        assertTrue(matcher.find(), "响应中缺少一次性下载地址");
        return matcher.group(1);
    }

    private void assertNoTemporaryExports() throws Exception {
        if (!Files.isDirectory(exportDir)) return;
        try (Stream<Path> paths = Files.list(exportDir)) {
            assertEquals(0, paths.filter(path -> path.getFileName().toString().startsWith(".love-space-export-"))
                    .count());
        }
    }

    private void clearDirectory(Path root) throws Exception {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) Files.deleteIfExists(path);
        }
    }

    private Map<String, byte[]> unzip(byte[] source) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                result.put(entry.getName(), output.toByteArray());
                zip.closeEntry();
            }
        }
        return result;
    }
}
