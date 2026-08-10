package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.lovespace.domain.*;
import com.lovespace.repository.*;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.junit.jupiter.api.io.TempDir;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoveSpaceApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired MemoryRepository memories;
    @Autowired MediaRepository media;
    @Autowired PasswordEncoder encoder;
    @Autowired FilterChainProxy filterChainProxy;
    @Autowired CookieCsrfTokenRepository csrfTokenRepository;
    @Value("${SETUP_TOKEN}") String setupToken;
    @Value("${PASSWORD_RESET_TOKEN}") String passwordResetToken;
    @TempDir static Path uploadDir;

    @DynamicPropertySource
    static void registerUploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDir.toString());
    }

    private MockHttpSession firstSession;
    private MockHttpSession secondSession;

    @BeforeEach
    void resetAndInitialize() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications", "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories", "moods", "users", "couples"}) {
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
        Path root = uploadDir;
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
    void initializedSetupRejectsWithoutComparingAnyToken() throws Exception {
        String setup = """
                {"spaceName":"重复初始化","loveStartedAt":"2025-02-14T20:00:00",
                 "firstUser":{"username":"alice","password":"alice-pass-123","nickname":"小爱"},
                 "secondUser":{"username":"bob","password":"bob-pass-123","nickname":"小宝"}}
                """;
        String withoutToken = mvc.perform(post("/api/setup/initialize").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_INITIALIZED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(withoutToken.contains("alice"));
        assertFalse(withoutToken.contains("alice-pass-123"));

        mvc.perform(post("/api/setup/initialize").with(csrf()).header("X-Setup-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_INITIALIZED"));
    }

    @Test
    void csrfFailuresHaveIndependentCodesAndDoNotReachTheController() throws Exception {
        String diary = "{\"title\":\"不会写入\",\"content\":\"csrf\",\"diaryDate\":\"2026-08-10\"}";

        mvc.perform(post("/api/diaries").session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON).content(diary))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_MISSING"));
        mvc.perform(post("/api/diaries").with(csrf().useInvalidToken()).session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON).content(diary))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        assertEquals(0, jdbc.queryForObject("select count(*) from diaries", Integer.class));
    }

    @Test
    void csrfCookieAndHeaderCompleteTheRealBrowserHandshake() throws Exception {
        CsrfFilter csrfFilter = filterChainProxy.getFilters("/api/auth/login").stream()
                .filter(CsrfFilter.class::isInstance).map(CsrfFilter.class::cast).findFirst().orElseThrow();
        Field tokenRepository = CsrfFilter.class.getDeclaredField("tokenRepository");
        tokenRepository.setAccessible(true);
        tokenRepository.set(csrfFilter, csrfTokenRepository);
        Object repository = tokenRepository.get(csrfFilter);
        assertTrue(repository instanceof CookieCsrfTokenRepository,
                "Unexpected CSRF repository: " + repository.getClass().getName());
        MvcResult csrfResponse = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();
        String body = csrfResponse.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertTrue(matcher.find(), "CSRF response should include a token");
        String token = matcher.group(1);
        Cookie cookie = csrfResponse.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "CSRF response should set the XSRF-TOKEN cookie");
        assertEquals(token, cookie.getValue());

        String diary = "{\"title\":\"真实握手\",\"content\":\"cookie + header\",\"diaryDate\":\"2026-08-10\"}";
        mvc.perform(post("/api/diaries").session(firstSession).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(diary))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        MvcResult browserLogin = mvc.perform(post("/api/auth/login").cookie(cookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice").param("password", "alice-pass-123"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession browserSession = (MockHttpSession) browserLogin.getRequest().getSession(false);
        mvc.perform(post("/api/diaries").session(browserSession).cookie(cookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(diary))
                .andExpect(status().isCreated());
    }

    @Test
    void realtimeSyncStreamRequiresLoginAndStartsForAuthenticatedClient() throws Exception {
        mvc.perform(get("/api/sync/stream").param("clientId", "client_test_123"))
                .andExpect(status().isUnauthorized());
        MvcResult stream = mvc.perform(get("/api/sync/stream").session(firstSession)
                        .param("clientId", "client_test_123"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        stream.getRequest().getAsyncContext().complete();
    }

    @Test
    void passwordRecoveryResetsPasswordAndInvalidatesExistingSession() throws Exception {
        MvcResult stream = mvc.perform(get("/api/sync/stream").session(firstSession)
                        .param("clientId", "client_reset_123"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(passwordResetRequest("alice", passwordResetToken, "short", "203.0.113.10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/auth/me").session(firstSession))
                .andExpect(status().isOk());

        mvc.perform(passwordResetRequest(
                        "alice", passwordResetToken, "alice-reset-pass-123", "203.0.113.10"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(firstSession))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGED"));
        mvc.perform(get("/api/sync/stream").session(firstSession).param("clientId", "client_reset_456"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGED"));
        login("alice", "alice-reset-pass-123");
    }

    @Test
    void passwordChangeInvalidatesOldApiSessionAndSseConnection() throws Exception {
        MvcResult stream = mvc.perform(get("/api/sync/stream").session(firstSession)
                        .param("clientId", "client_change_123"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        MockHttpSession passwordSession = login("alice", "alice-pass-123");

        mvc.perform(put("/api/profile/password").with(csrf()).session(passwordSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"alice-pass-123\",\"newPassword\":\"alice-changed-pass-123\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(passwordSession))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGED"));
        mvc.perform(get("/api/sync/stream").session(firstSession).param("clientId", "client_change_456"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGED"));
    }

    @Test
    void passwordRecoveryDoesNotRevealWhetherAccountExists() throws Exception {
        mvc.perform(passwordResetRequest(
                        "alice", "wrong-token", "another-pass-123", "203.0.113.20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"))
                .andExpect(jsonPath("$.message").value("账号或恢复口令不正确"));
        mvc.perform(passwordResetRequest(
                        "nobody", passwordResetToken, "another-pass-123", "203.0.113.20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"))
                .andExpect(jsonPath("$.message").value("账号或恢复口令不正确"));
    }

    @Test
    void passwordRecoveryLimitsRepeatedFailuresByIdentityAndIp() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(passwordResetRequest(
                            "alice", "wrong-token", "another-pass-123", "203.0.113.30"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"));
        }
        mvc.perform(passwordResetRequest(
                        "alice", "wrong-token", "another-pass-123", "203.0.113.30"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_RATE_LIMITED"));

        for (int attempt = 0; attempt < 4; attempt++) {
            mvc.perform(passwordResetRequest(
                            "unknown" + attempt, "wrong-token", "another-pass-123", "203.0.113.40"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"));
        }
        mvc.perform(passwordResetRequest(
                        "unknown4", "wrong-token", "another-pass-123", "203.0.113.40"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_RATE_LIMITED"));
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
                ("{\"title\":\"海边\",\"description\":\"第一次看海\",\"eventAt\":\"2026-07-01T18:30:00\",\"location\":\"青岛\","
                        + "\"tags\":[\"旅行\",\"海边\"]}")
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "sea.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        String memory = mvc.perform(multipart("/api/memories").file(data).file(file).with(csrf()).session(firstSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media[0].mediaType").value("image"))
                .andExpect(jsonPath("$.location").value("青岛"))
                .andExpect(jsonPath("$.tags", containsInAnyOrder("旅行", "海边")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long mediaId = nestedId(memory, "media");
        MockMultipartFile video = new MockMultipartFile("files", "sunset.mp4", "video/mp4",
                new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0,
                        'i', 's', 'o', 'm', 'm', 'p', '4', '2'});
        mvc.perform(multipart("/api/memories/{id}/media", idOf(memory)).file(video).with(csrf()).session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[?(@.mediaType == 'video')]").isNotEmpty());
        mvc.perform(get("/api/memories/tags").session(secondSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.name == '旅行')].memoryCount").value(hasItem(1)));
        mvc.perform(get("/api/memories").session(secondSession).param("tag", "海边"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/memories/album").session(secondSession).param("tag", "旅行"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].memoryTitle").value("海边"))
                .andExpect(jsonPath("$.content[0].media.mediaType").value("video"))
                .andExpect(jsonPath("$.content[1].media.mediaType").value("image"));
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

        MockMultipartFile mislabeledJpeg = new MockMultipartFile("files", "camera.jpg", "image/png",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 16, 0, 0});
        mvc.perform(multipart("/api/memories").file(data).file(mislabeledJpeg).with(csrf()).session(firstSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.media[0].mediaType").value("image"));

        MockMultipartFile genericHeic = new MockMultipartFile("files", "phone.heic", "application/octet-stream",
                new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'i', 'f', '1', 0, 0, 0, 0,
                        'h', 'e', 'i', 'c', 0, 0, 0, 0});
        mvc.perform(multipart("/api/memories").file(data).file(genericHeic).with(csrf()).session(firstSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media[0].contentType").value("image/heic"))
                .andExpect(jsonPath("$.media[0].mediaType").value("image"));
    }

    @Test
    void memoryTagsDeduplicateCaseInsensitivelyAndKeepFirstDisplaySpelling() throws Exception {
        String json = """
                {"title":"标签大小写","eventAt":"2026-07-01T18:30:00",
                 "tags":["Trip"," trip ","TRIP"]}
                """;
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                json.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/memories").file(data).with(csrf()).session(firstSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags", contains("Trip")));
        User alice = users.findByUsernameIgnoreCase("alice").orElseThrow();
        Memory another = new Memory();
        another.setCoupleId(alice.getCouple().getId());
        another.setAuthorId(alice.getId());
        another.setTitle("第二个标签回忆");
        another.setEventAt(LocalDateTime.of(2026, 7, 2, 18, 30));
        another.setTags(List.of("trip"));
        memories.saveAndFlush(another);
        mvc.perform(get("/api/memories/tags").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Trip"))
                .andExpect(jsonPath("$[0].memoryCount").value(2));
        mvc.perform(get("/api/memories").session(secondSession).param("tag", "trip"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void albumAndTimelineUseDatabaseFilteringPagingAndHandleHugePages() throws Exception {
        User alice = users.findByUsernameIgnoreCase("alice").orElseThrow();
        for (int index = 0; index < 24; index++) {
            Memory memory = new Memory();
            memory.setCoupleId(alice.getCouple().getId());
            memory.setAuthorId(alice.getId());
            memory.setTitle(index < 18 ? "目标回忆 " + index : "其他回忆 " + index);
            memory.setDescription(index < 18 ? "关键词描述" : "无关描述");
            memory.setEventAt(LocalDateTime.of(2026, 7, 1, 18, 30).plusDays(index));
            memory.setTags(List.of(index < 18 ? "Trip" : "Other"));
            memories.saveAndFlush(memory);

            Media image = new Media();
            image.setCoupleId(alice.getCouple().getId());
            image.setOwnerId(alice.getId());
            image.setMemoryId(memory.getId());
            image.setStoredName(UUID.randomUUID().toString());
            image.setOriginalName("memory-" + index + ".png");
            image.setContentType("image/png");
            image.setMediaType("image");
            image.setByteSize(1);
            media.save(image);
        }

        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("q", "关键词").param("tag", "trip")
                        .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(18))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].memoryTitle").value("目标回忆 17"));
        mvc.perform(get("/api/memories").session(secondSession)
                        .param("q", "关键词").param("tag", "TRIP")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(18))
                .andExpect(jsonPath("$.content", hasSize(5)));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("q", "关键词").param("tag", "trip")
                        .param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(18))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/memories").session(secondSession)
                        .param("q", "关键词").param("tag", "trip")
                        .param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(18))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void albumSupportsDefaultMixedFilteringPagingDeletionAndCoupleIsolation() throws Exception {
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));

        User alice = users.findByUsernameIgnoreCase("alice").orElseThrow();
        Memory photoVideo = albumMemory(alice, "相册标题", "描述关键词", "上海外滩",
                LocalDateTime.of(2026, 8, 10, 18, 30), List.of("Trip Day"));
        addAlbumMedia(alice, photoVideo, "image", "photo.png");
        addAlbumMedia(alice, photoVideo, "video", "video.mp4");

        Memory description = albumMemory(alice, "描述标题", "描述关键词", "杭州",
                LocalDateTime.of(2026, 8, 9, 18, 30), List.of("Trip Day"));
        addAlbumMedia(alice, description, "image", "description.png");

        Memory location = albumMemory(alice, "地点标题", "没有匹配", "上海外滩",
                LocalDateTime.of(2026, 8, 8, 18, 30), List.of("Other"));
        addAlbumMedia(alice, location, "image", "location.png");

        Memory tag = albumMemory(alice, "标签标题", "没有匹配", "南京",
                LocalDateTime.of(2026, 8, 7, 18, 30), List.of("Trip Day"));
        addAlbumMedia(alice, tag, "image", "tag.png");

        Memory deleted = albumMemory(alice, "已删除回忆", "描述关键词", "上海外滩",
                LocalDateTime.of(2026, 8, 11, 18, 30), List.of("Trip Day"));
        deleted.setDeletedAt(LocalDateTime.of(2026, 8, 12, 18, 30));
        memories.saveAndFlush(deleted);
        addAlbumMedia(alice, deleted, "image", "deleted.png");

        Memory audioOnly = albumMemory(alice, "音频回忆", "声音描述", "录音室",
                LocalDateTime.of(2026, 8, 6, 18, 30), List.of("Other"));
        addAlbumMedia(alice, audioOnly, "audio", "audio.mp3");

        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].memoryTitle").value("相册标题"))
                .andExpect(jsonPath("$.content[0].media.mediaType").value("video"))
                .andExpect(jsonPath("$.content[1].media.mediaType").value("image"))
                .andExpect(jsonPath("$.content[2].memoryTitle").value("描述标题"))
                .andExpect(jsonPath("$.content[4].memoryTitle").value("标签标题"));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("q", "相册标题").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("q", "描述关键词").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("q", "上海外滩").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("tag", "  trip   day ").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].memoryTitle").value("描述标题"))
                .andExpect(jsonPath("$.content[1].memoryTitle").value("地点标题"));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("q", "已删除回忆").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/memories/album").session(secondSession)
                        .param("q", "音频回忆").param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/memories/album").session(secondSession).param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/memories/album").session(secondSession).param("size", "101"))
                .andExpect(status().isBadRequest());

        Couple outsiderCouple = new Couple();
        outsiderCouple.setSpaceName("另一个空间");
        outsiderCouple.setLoveStartedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        couples.saveAndFlush(outsiderCouple);
        User outsider = new User();
        outsider.setCouple(outsiderCouple);
        outsider.setUsername("album-outsider");
        outsider.setNickname("相册路人");
        outsider.setPasswordHash(encoder.encode("outsider-pass"));
        users.saveAndFlush(outsider);
        User outsiderPartner = new User();
        outsiderPartner.setCouple(outsiderCouple);
        outsiderPartner.setUsername("album-outsider-2");
        outsiderPartner.setNickname("相册路人2");
        outsiderPartner.setPasswordHash(encoder.encode("outsider-pass-2"));
        users.saveAndFlush(outsiderPartner);
        MockHttpSession outsiderSession = login("album-outsider", "outsider-pass");
        mvc.perform(get("/api/memories/album").session(outsiderSession)
                        .param("page", "0").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
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
    private MockHttpServletRequestBuilder passwordResetRequest(
            String username, String recoveryToken, String newPassword, String remoteAddress) {
        String body = "{\"username\":\"" + username + "\",\"recoveryToken\":\"" + recoveryToken
                + "\",\"newPassword\":\"" + newPassword + "\"}";
        return post("/api/auth/reset-password").with(csrf())
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
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

    private Memory albumMemory(User owner, String title, String description, String location,
                               LocalDateTime eventAt, List<String> tags) {
        Memory memory = new Memory();
        memory.setCoupleId(owner.getCouple().getId());
        memory.setAuthorId(owner.getId());
        memory.setTitle(title);
        memory.setDescription(description);
        memory.setEventAt(eventAt);
        memory.setLocation(location);
        memory.setTags(tags);
        return memories.saveAndFlush(memory);
    }

    private Media addAlbumMedia(User owner, Memory memory, String mediaType, String originalName) {
        Media value = new Media();
        value.setCoupleId(owner.getCouple().getId());
        value.setOwnerId(owner.getId());
        value.setMemoryId(memory.getId());
        value.setStoredName(UUID.randomUUID().toString());
        value.setOriginalName(originalName);
        value.setContentType("audio".equals(mediaType) ? "audio/mpeg"
                : "video".equals(mediaType) ? "video/mp4" : "image/png");
        value.setMediaType(mediaType);
        value.setByteSize(1);
        return media.saveAndFlush(value);
    }
}
