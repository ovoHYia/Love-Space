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
class GameEndCoverageIntegrationTest {
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
    void canvasClearIsDrawerOnlyAndFinishEndsTheGame() throws Exception {
        long gameId = idOf(mvc.perform(post("/api/games").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"DRAW_GUESS\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        mvc.perform(delete("/api/games/{id}/canvas", gameId).with(csrf()).session(bob)
                        .param("roundNumber", "1"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/games/{id}/canvas", gameId).with(csrf()).session(alice)
                        .param("roundNumber", "1"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/games/{id}/finish", gameId).with(csrf()).session(outsider))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/games/{id}/finish", gameId).with(csrf()).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));

        mvc.perform(get("/api/games/{id}", gameId).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
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
}
