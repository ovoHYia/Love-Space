package com.lovespace;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class GameIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private MockHttpSession alice;
    private MockHttpSession bob;
    private Long aliceId;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Couple couple = new Couple();
        couple.setSpaceName("我们的小时光");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.save(couple);
        aliceId = newUser(couple, "alice", "小爱", "alice-pass-123").getId();
        newUser(couple, "bob", "小宝", "bob-pass-123");
        alice = login("alice", "alice-pass-123");
        bob = login("bob", "bob-pass-123");
    }

    @Test
    void tacitAnswersStayHiddenUntilBothSubmit() throws Exception {
        long gameId = createGame(alice, "TACIT_QUIZ");

        mvc.perform(post("/api/games/{id}/answer", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"宅家看电影\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myAnswer").value("宅家看电影"))
                .andExpect(jsonPath("$.partnerAnswer").value(nullValue()))
                .andExpect(jsonPath("$.answersRevealed").value(false));

        mvc.perform(get("/api/games/{id}", gameId).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myAnswer").value(nullValue()))
                .andExpect(jsonPath("$.partnerAnswer").value(nullValue()));

        mvc.perform(post("/api/games/{id}/answer", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"宅家看电影\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answersRevealed").value(true))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.score").value(1));

        mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.answersRevealed").value(false))
                .andExpect(jsonPath("$.score").value(1));
    }

    @Test
    void drawingAndGuessingRespectRolesAndSwitchEachRound() throws Exception {
        long gameId = createGame(alice, "DRAW_GUESS");

        mvc.perform(get("/api/games/{id}", gameId).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTurnUserId").value(aliceId))
                .andExpect(jsonPath("$.secretWord").value("奶茶"));
        mvc.perform(get("/api/games/{id}", gameId).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretWord").value(nullValue()));

        String stroke = """
                {"strokes":[{"color":"#c95868","width":5,
                "points":[{"x":0.1,"y":0.2},{"x":0.3,"y":0.4}]}]}
                """;
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content(stroke))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(stroke))
                .andExpect(status().isOk()).andExpect(jsonPath("$.strokes", hasSize(1)));

        mvc.perform(post("/api/games/{id}/guess", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guess\":\"奶茶\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/games/{id}/guess", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guess\":\"咖啡\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roundComplete").value(false));
        mvc.perform(post("/api/games/{id}/guess", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guess\":\"奶茶\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundComplete").value(true))
                .andExpect(jsonPath("$.secretWord").value("奶茶"))
                .andExpect(jsonPath("$.score").value(1));

        mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.secretWord").value("玫瑰"))
                .andExpect(jsonPath("$.strokes", hasSize(0)));
    }

    @Test
    void gamesAreCoupleScoped() throws Exception {
        long gameId = createGame(alice, "TACIT_QUIZ");

        Couple other = new Couple();
        other.setSpaceName("另一间小屋");
        other.setLoveStartedAt(LocalDateTime.now().minusDays(10));
        couples.save(other);
        newUser(other, "outsider", "访客", "outsider-pass-123");
        newUser(other, "outsider_partner", "访客伴侣", "partner-pass-123");
        MockHttpSession outsider = login("outsider", "outsider-pass-123");

        mvc.perform(get("/api/games").session(outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/games/{id}", gameId).session(outsider))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(outsider))
                .andExpect(status().isNotFound());
    }

    private long createGame(MockHttpSession session, String gameType) throws Exception {
        String response = mvc.perform(post("/api/games").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"" + gameType + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(response);
        if (!matcher.find()) throw new AssertionError("response has no id: " + response);
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
