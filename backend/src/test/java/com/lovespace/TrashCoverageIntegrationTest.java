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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrashCoverageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

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
        newUser(couple, "alice", "小爱", "alice-pass-123");
        newUser(couple, "bob", "小宝", "bob-pass-123");
        alice = login("alice", "alice-pass-123");
        bob = login("bob", "bob-pass-123");
    }

    @Test
    void purgeRemovesSingleTypeAndRestoreAfterPurgeIsNotFound() throws Exception {
        long diaryId = idOf(create("/api/diaries", "{\"title\":\"日记\",\"content\":\"内容\",\"diaryDate\":\"2026-08-22\",\"mood\":\"温柔\"}"));
        long wishId = idOf(create("/api/wishes", "{\"title\":\"愿望\",\"category\":\"OTHER\"}"));

        mvc.perform(delete("/api/diaries/{id}", diaryId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/wishes/{id}", wishId).with(csrf()).session(alice)).andExpect(status().isNoContent());

        mvc.perform(get("/api/trash").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(delete("/api/trash/DIARY/{id}", diaryId).with(csrf()).session(alice))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/trash/DIARY/{id}/restore", diaryId).with(csrf()).session(alice))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/trash").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("WISH"));

        mvc.perform(post("/api/trash/WISH/{id}/restore", wishId).with(csrf()).session(bob))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/trash/WISH/{id}/restore", wishId).with(csrf()).session(alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/wishes").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void emptyTrashPurgesEverythingForTheRequestingUser() throws Exception {
        long diaryId = idOf(create("/api/diaries", "{\"title\":\"日记一\",\"content\":\"内容\",\"diaryDate\":\"2026-08-21\",\"mood\":\"温柔\"}"));
        long anniversaryId = idOf(create("/api/anniversaries", "{\"title\":\"纪念日\",\"eventDate\":\"2026-02-14\",\"type\":\"CUSTOM\",\"recurringYearly\":true,\"reminderDays\":7}"));
        long wishId = idOf(create("/api/wishes", "{\"title\":\"愿望一\",\"category\":\"OTHER\"}"));

        mvc.perform(delete("/api/diaries/{id}", diaryId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/anniversaries/{id}", anniversaryId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/wishes/{id}", wishId).with(csrf()).session(alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/trash").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mvc.perform(delete("/api/trash").with(csrf()).session(alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/trash").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/diaries").session(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/anniversaries").session(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/wishes").session(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    private String create(String path, String body) throws Exception {
        return mvc.perform(post(path).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long idOf(String json) {
        Matcher matcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("response has no id: " + json);
        return Long.parseLong(matcher.group(1));
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
}
