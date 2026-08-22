package com.lovespace;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lovespace.domain.Couple;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.UserRepository;
import java.time.LocalDateTime;
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
class MediaIntegrityHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private MockHttpSession alice;

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
    }

    @Test
    void scanRequiresAuthenticationAndReturnsSummary() throws Exception {
        mvc.perform(post("/api/data/media-integrity").with(csrf()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/data/media-integrity").with(csrf()).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedRecords").value(0))
                .andExpect(jsonPath("$.checkedAt").isNotEmpty());
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
