package com.lovespace;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lovespace.domain.Couple;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentUpdateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private MockHttpSession alice;
    private MockHttpSession bob;
    private MockHttpSession outsider;

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
        newUser(couple, "alice", "小爱", "alice-pass-123");
        newUser(couple, "bob", "小宝", "bob-pass-123");
        alice = login("alice", "alice-pass-123");
        bob = login("bob", "bob-pass-123");

        Couple other = new Couple();
        other.setSpaceName("另一间小屋");
        other.setLoveStartedAt(LocalDateTime.now().minusDays(10));
        couples.save(other);
        newUser(other, "outsider", "访客", "outsider-pass-123");
        newUser(other, "outsider_partner", "访客伴侣", "partner-pass-123");
        outsider = login("outsider", "outsider-pass-123");
    }

    @Test
    void anniversaryUpdateHonorsVersionAndCoupleScope() throws Exception {
        String created = mvc.perform(post("/api/anniversaries").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"在一起一周年\",\"eventDate\":\"2026-02-14\",\"type\":\"CUSTOM\",\"recurringYearly\":true,\"reminderDays\":7}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = idOf(created);
        long version = longField(created, "version");

        mvc.perform(put("/api/anniversaries/{id}", id).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"在一起一周年啦\",\"eventDate\":\"2026-02-14\",\"type\":\"CUSTOM\",\"recurringYearly\":true,\"reminderDays\":3,\"version\":%d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("在一起一周年啦"))
                .andExpect(jsonPath("$.reminderDays").value(3));

        mvc.perform(put("/api/anniversaries/{id}", id).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"旧版本覆盖\",\"eventDate\":\"2026-02-14\",\"type\":\"CUSTOM\",\"recurringYearly\":true,\"reminderDays\":7,\"version\":%d}".formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_UPDATE"));

        mvc.perform(put("/api/anniversaries/{id}", id).with(csrf()).session(outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权修改\",\"eventDate\":\"2026-02-14\",\"type\":\"CUSTOM\",\"recurringYearly\":true,\"reminderDays\":7,\"version\":%d}".formatted(version)))
                .andExpect(status().isNotFound());
    }

    @Test
    void diaryUpdateHonorsVersionAndCoupleScope() throws Exception {
        String created = mvc.perform(post("/api/diaries").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"今天\",\"content\":\"很开心\",\"diaryDate\":\"2026-08-22\",\"mood\":\"温柔\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = idOf(created);
        long version = longField(created, "version");

        mvc.perform(put("/api/diaries/{id}", id).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"伴侣不能改\",\"content\":\"覆盖\",\"diaryDate\":\"2026-08-22\",\"mood\":\"温柔\",\"version\":%d}".formatted(version)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/diaries/{id}", id).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"今天呀\",\"content\":\"超级开心\",\"diaryDate\":\"2026-08-22\",\"mood\":\"温柔\",\"version\":%d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("今天呀"));

        mvc.perform(put("/api/diaries/{id}", id).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"旧内容\",\"content\":\"覆盖\",\"diaryDate\":\"2026-08-22\",\"mood\":\"温柔\",\"version\":%d}".formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_UPDATE"));

        mvc.perform(put("/api/diaries/{id}", id).with(csrf()).session(outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权\",\"content\":\"覆盖\",\"diaryDate\":\"2026-08-22\",\"mood\":\"温柔\",\"version\":%d}".formatted(version)))
                .andExpect(status().isNotFound());
    }

    @Test
    void memoryUpdateSupportsJsonAndMultipartWithStaleConflict() throws Exception {
        String created = mvc.perform(multipart("/api/memories")
                        .file(new MockMultipartFile("data", "", "application/json",
                                "{\"title\":\"第一次旅行\",\"description\":\"\",\"eventAt\":\"2025-05-01T20:00:00+08:00\",\"eventTimeKnown\":true,\"location\":\"\",\"tags\":[]}".getBytes(StandardCharsets.UTF_8)))
                        .with(csrf()).session(alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = idOf(created);
        long version = longField(created, "version");

        mvc.perform(put("/api/memories/{id}", id).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memoryJson(version)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/memories/{id}", id).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memoryJson(version).replace("第一次旅行", "第一次自驾")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第一次自驾"));
        long versionAfterJson = longField(mvc.perform(get("/api/memories/{id}", id).session(alice))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "version");

        mvc.perform(multipart("/api/memories/{id}", id)
                        .file(new MockMultipartFile("data", "", "application/json",
                                memoryJson(versionAfterJson).getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("files", "note.png", "image/png", pngBytes()))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf()).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media", org.hamcrest.Matchers.hasSize(1)));

        mvc.perform(put("/api/memories/{id}", id).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memoryJson(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_UPDATE"));

        mvc.perform(put("/api/memories/{id}", id).with(csrf()).session(outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memoryJson(version)))
                .andExpect(status().isNotFound());
    }

    private String memoryJson(long version) {
        return "{\"title\":\"第一次旅行\",\"description\":\"\",\"eventAt\":\"2025-05-01T20:00:00+08:00\","
                + "\"eventTimeKnown\":true,\"location\":\"\",\"tags\":[],\"version\":" + version + "}";
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private User newUser(Couple couple, String username, String nickname, String password) {
        User user = new User();
        user.setCouple(couple);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash(encoder.encode(password));
        return users.save(user);
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

    private long longField(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("response has no " + field + ": " + json);
        return Long.parseLong(matcher.group(1));
    }
}
