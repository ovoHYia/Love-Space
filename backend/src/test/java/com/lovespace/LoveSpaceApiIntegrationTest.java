package com.lovespace;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.lovespace.domain.*;
import com.lovespace.repository.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoveSpaceApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired MemoryRepository memories;
    @Autowired PasswordEncoder encoder;
    @Value("${SETUP_TOKEN}") String setupToken;
    @Value("${PASSWORD_RESET_TOKEN}") String passwordResetToken;
    @Value("${app.upload-dir}") String uploadDir;

    private MockHttpSession firstSession;
    private MockHttpSession secondSession;

    @BeforeEach
    void resetAndInitialize() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"notification_preferences", "notifications", "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories", "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        String setup = """
                {"spaceName":"我们的小时光","loveStartedAt":"2025-02-14T20:00:00",
                 "firstUser":{"username":"alice","password":"alice-pass-123","nickname":"小爱"},
                 "secondUser":{"username":"bob","password":"bob-pass-123","nickname":"小宝"}}
                """;
        mvc.perform(post("/api/setup/initialize").header("X-Setup-Token", setupToken)
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/setup/initialize").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_SETUP_TOKEN"));
        mvc.perform(post("/api/setup/initialize").with(csrf()).header("X-Setup-Token", setupToken)
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.partner.username").value("bob"));
        firstSession = login("alice", "alice-pass-123");
        secondSession = login("bob", "bob-pass-123");
    }

    @AfterEach
    void removeUploadedFiles() throws Exception {
        Path root = Path.of(uploadDir);
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void setupSessionAndUniformErrorsWork() throws Exception {
        mvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.initialized").value(true));
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/api/auth/me").session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("小爱"))
                .andExpect(jsonPath("$.partner.nickname").value("小宝"));
        mvc.perform(put("/api/space").with(csrf()).session(firstSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceName\":\"我们的新小屋\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.spaceName").value("我们的新小屋"));
        mvc.perform(get("/api/auth/me").session(secondSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.couple.spaceName").value("我们的新小屋"));
        mvc.perform(get("/api/messages").session(firstSession).param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put("/api/profile").with(csrf()).session(firstSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.nickname").exists());
        mvc.perform(post("/api/auth/logout").with(csrf()).session(firstSession)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(firstSession)).andExpect(status().isUnauthorized());

        MockHttpSession passwordSession = login("alice", "alice-pass-123");
        mvc.perform(put("/api/profile/password").with(csrf()).session(passwordSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"alice-pass-123\",\"newPassword\":\"alice-new-pass-123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice").param("password", "alice-pass-123"))
                .andExpect(status().isUnauthorized());
        login("alice", "alice-new-pass-123");
    }

    @Test
    void passwordRecoveryResetsPasswordAndInvalidatesExistingSession() throws Exception {
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"recoveryToken\":\"" + passwordResetToken
                                + "\",\"newPassword\":\"alice-reset-pass-123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(firstSession))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGED"));
        login("alice", "alice-reset-pass-123");
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"recoveryToken\":\"wrong\",\"newPassword\":\"another-pass-123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"));
    }

    @Test
    void diaryAndMessageOwnershipRulesAreEnforced() throws Exception {
        String diary = mvc.perform(post("/api/diaries").with(csrf()).session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"今天\",\"content\":\"很幸福\",\"diaryDate\":\"2026-07-16\",\"mood\":\"开心\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long diaryId = idOf(diary);
        mvc.perform(get("/api/diaries").session(secondSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].content").value("很幸福"));
        mvc.perform(delete("/api/diaries/{id}", diaryId).with(csrf()).session(secondSession))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));

        String letter = mvc.perform(post("/api/messages").with(csrf()).session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"晚安，想你\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.recipientNickname").value("小宝"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long messageId = idOf(letter);
        mvc.perform(get("/api/dashboard").session(secondSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recentMessages[0].content").doesNotExist());
        mvc.perform(patch("/api/messages/{id}/read", messageId).with(csrf()).session(firstSession))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/messages/{id}/read", messageId).with(csrf()).session(secondSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").isNotEmpty())
                .andExpect(jsonPath("$.content").value("晚安，想你"));
        mvc.perform(delete("/api/messages/{id}", messageId).with(csrf()).session(secondSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void mediaIsProtectedAndCoupleScoped() throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                "{\"title\":\"海边\",\"description\":\"第一次看海\",\"eventAt\":\"2026-07-01T18:30:00\",\"location\":\"青岛\"}"
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "sea.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        String memory = mvc.perform(multipart("/api/memories").file(data).file(file).with(csrf()).session(firstSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media[0].mediaType").value("image"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long mediaId = nestedId(memory, "media");
        mvc.perform(get("/api/memories/random").session(firstSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("海边"));
        User alice = users.findByUsernameIgnoreCase("alice").orElseThrow();
        Memory alternate = new Memory();
        alternate.setCoupleId(alice.getCouple().getId()); alternate.setAuthorId(alice.getId()); alternate.setTitle("另一段回忆");
        alternate.setEventAt(LocalDateTime.of(2026, 7, 2, 18, 30));
        memories.save(alternate);
        mvc.perform(get("/api/memories/random").session(firstSession).param("excludeId", String.valueOf(idOf(memory))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("另一段回忆"));
        mvc.perform(get("/api/media/{id}", mediaId).session(secondSession))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/api/media/{id}", mediaId)).andExpect(status().isUnauthorized());

        Couple outsiderCouple = new Couple(); outsiderCouple.setSpaceName("别的空间");
        outsiderCouple.setLoveStartedAt(LocalDateTime.of(2024, 1, 1, 0, 0)); couples.save(outsiderCouple);
        User outsider = new User(); outsider.setCouple(outsiderCouple); outsider.setUsername("outsider");
        outsider.setNickname("路人"); outsider.setPasswordHash(encoder.encode("outsider-pass")); users.save(outsider);
        User outsiderPartner = new User(); outsiderPartner.setCouple(outsiderCouple); outsiderPartner.setUsername("outsider2");
        outsiderPartner.setNickname("路人2"); outsiderPartner.setPasswordHash(encoder.encode("outsider2-pass")); users.save(outsiderPartner);
        MockHttpSession outsiderSession = login("outsider", "outsider-pass");
        mvc.perform(get("/api/media/{id}", mediaId).session(outsiderSession))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/memories").session(outsiderSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        MockMultipartFile bad = new MockMultipartFile("files", "payload.svg", "image/svg+xml", "<svg/>".getBytes());
        mvc.perform(multipart("/api/memories").file(data).file(bad).with(csrf()).session(firstSession))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        MockMultipartFile spoofed = new MockMultipartFile("files", "payload.png", "image/png", "<script/>".getBytes());
        mvc.perform(multipart("/api/memories").file(data).file(spoofed).with(csrf()).session(firstSession))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_CONTENT"));
    }

    @Test
    void dashboardMoodAnniversaryAndMemoryFiltersWork() throws Exception {
        mvc.perform(put("/api/moods/today").with(csrf()).session(firstSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"😊\",\"label\":\"开心\",\"note\":\"见到你啦\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("开心"));
        mvc.perform(post("/api/anniversaries").with(csrf()).session(firstSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"恋爱纪念日\",\"eventDate\":\"2025-02-14\",\"type\":\"LOVE\",\"recurringYearly\":true,\"reminderDays\":365,\"note\":\"一起吃饭\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.daysUntil").isNumber());
        mvc.perform(get("/api/dashboard").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayMoods[0].emoji").value("😊"))
                .andExpect(jsonPath("$.anniversaries[0].title").value("恋爱纪念日"))
                .andExpect(jsonPath("$.dueReminders[0].title").value("恋爱纪念日"));
        User alice = users.findByUsernameIgnoreCase("alice").orElseThrow();
        Memory first = new Memory();
        first.setCoupleId(alice.getCouple().getId()); first.setAuthorId(alice.getId()); first.setTitle("当天的回忆");
        first.setEventAt(LocalDateTime.of(2026, 7, 22, 8, 15)); memories.save(first);
        Memory second = new Memory();
        second.setCoupleId(alice.getCouple().getId()); second.setAuthorId(alice.getId()); second.setTitle("另一天的回忆");
        second.setEventAt(LocalDateTime.of(2026, 7, 23, 8, 15)); memories.save(second);
        mvc.perform(get("/api/memories").session(firstSession).param("date", "2026-07-22"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("当天的回忆"));
        mvc.perform(get("/api/memories").session(firstSession).param("date", "2026-07-24"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/memories").session(firstSession).param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void dashboardReturnsOnlyFourRecentDiariesAndMessages() throws Exception {
        for (int index = 1; index <= 5; index++) {
            String date = String.format("2026-07-%02d", index);
            mvc.perform(post("/api/diaries").with(csrf()).session(firstSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"日记 " + index + "\",\"content\":\"内容 " + index
                                    + "\",\"diaryDate\":\"" + date + "\",\"mood\":\"开心\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/messages").with(csrf()).session(firstSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"留言 " + index + "\"}"))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/api/dashboard").session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentDiaries", hasSize(4)))
                .andExpect(jsonPath("$.recentMessages", hasSize(4)));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username).param("password", password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    private long idOf(String json) {
        Matcher matcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("response has no id: " + json);
        return Long.parseLong(matcher.group(1));
    }
    private long nestedId(String json, String property) {
        Matcher matcher = Pattern.compile("\\\"" + property + "\\\":\\[\\{\\\"id\\\":(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("response has no nested id: " + json);
        return Long.parseLong(matcher.group(1));
    }
}
