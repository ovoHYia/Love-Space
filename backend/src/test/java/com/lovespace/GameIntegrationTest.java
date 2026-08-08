package com.lovespace;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lovespace.domain.Couple;
import com.lovespace.domain.GameSession;
import com.lovespace.domain.Media;
import com.lovespace.domain.Memory;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.GameSessionRepository;
import com.lovespace.repository.MediaRepository;
import com.lovespace.repository.MemoryRepository;
import com.lovespace.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired GameSessionRepository games;
    @Autowired MemoryRepository memories;
    @Autowired MediaRepository media;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    private MockHttpSession alice;
    private MockHttpSession bob;
    private Couple couple;
    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        couple = new Couple();
        couple.setSpaceName("我们的小时光");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.save(couple);
        aliceId = newUser(couple, "alice", "小爱", "alice-pass-123").getId();
        bobId = newUser(couple, "bob", "小宝", "bob-pass-123").getId();
        alice = login("alice", "alice-pass-123");
        bob = login("bob", "bob-pass-123");
    }

    @Test
    void tacitAnswersStayHiddenUntilBothSubmit() throws Exception {
        long gameId = createGame(alice, "TACIT_QUIZ");
        JsonNode firstRound = getGame(alice, gameId);
        String firstPrompt = firstRound.get("prompt").asText();
        String answer = firstRound.get("options").get(0).asText();
        String answerBody = objectMapper.writeValueAsString(Map.of("answer", answer));

        mvc.perform(post("/api/games/{id}/answer", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(answerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myAnswer").value(answer))
                .andExpect(jsonPath("$.partnerAnswer").value(nullValue()))
                .andExpect(jsonPath("$.answersRevealed").value(false));

        mvc.perform(post("/api/games/{id}/answer", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(answerBody))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/games/{id}", gameId).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myAnswer").value(nullValue()))
                .andExpect(jsonPath("$.partnerAnswer").value(nullValue()));

        mvc.perform(post("/api/games/{id}/answer", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content(answerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answersRevealed").value(true))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.score").value(1));

        MvcResult nextRoundResult = mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.answersRevealed").value(false))
                .andExpect(jsonPath("$.score").value(1))
                .andReturn();
        String nextPrompt = objectMapper.readTree(nextRoundResult.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("prompt").asText();
        assertNotEquals(firstPrompt, nextPrompt, "相邻两轮不应抽到同一道默契题");
    }

    @Test
    void creatingSameGameTypeReusesActiveSession() throws Exception {
        long gameId = createGame(alice, "TACIT_QUIZ");

        mvc.perform(post("/api/games").with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"TACIT_QUIZ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(gameId));
    }

    @Test
    void memoryGuessKeepsTheAnswerHiddenUntilBothPlayersSubmit() throws Exception {
        createMemory("海边看日落", "那天一起追着晚霞走了很久。",
                LocalDateTime.of(2025, 5, 20, 18, 30), "厦门海边", true);
        createMemory("山里看星星", "第一次一起在帐篷外等流星。",
                LocalDateTime.of(2025, 8, 9, 22, 0), "莫干山", true);

        long gameId = createGame(alice, "MEMORY_GUESS");
        JsonNode initial = getGame(alice, gameId);
        String firstImage = initial.path("memory").path("imageUrl").asText();
        org.junit.jupiter.api.Assertions.assertFalse(firstImage.isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(initial.path("secretWord").isNull());
        org.junit.jupiter.api.Assertions.assertTrue(initial.path("memory").path("title").isNull());
        org.junit.jupiter.api.Assertions.assertTrue(initial.path("memory").path("description").isNull());
        org.junit.jupiter.api.Assertions.assertTrue(initial.path("memory").path("eventAt").isNull());
        org.junit.jupiter.api.Assertions.assertTrue(initial.path("memory").path("location").isNull());

        GameSession stored = games.findById(gameId).orElseThrow();
        String correct = objectMapper.readTree(stored.getStateJson()).path("correctAnswer").asText();
        String answerBody = objectMapper.writeValueAsString(Map.of("answer", correct));
        mvc.perform(post("/api/games/{id}/answer", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(answerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myAnswer").value(correct))
                .andExpect(jsonPath("$.partnerAnswer").value(nullValue()))
                .andExpect(jsonPath("$.secretWord").value(nullValue()))
                .andExpect(jsonPath("$.memory.title").value(nullValue()));
        mvc.perform(get("/api/games/{id}", gameId).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerAnswer").value(nullValue()))
                .andExpect(jsonPath("$.secretWord").value(nullValue()))
                .andExpect(jsonPath("$.memory.location").value(nullValue()));

        mvc.perform(post("/api/games/{id}/answer", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content(answerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answersRevealed").value(true))
                .andExpect(jsonPath("$.secretWord").value(correct))
                .andExpect(jsonPath("$.memory.title").isNotEmpty())
                .andExpect(jsonPath("$.memory.eventAt").isNotEmpty())
                .andExpect(jsonPath("$.score").value(2));

        MvcResult next = mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(alice)
                        .queryParam("roundNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.secretWord").value(nullValue()))
                .andExpect(jsonPath("$.memory.title").value(nullValue()))
                .andReturn();
        String nextImage = objectMapper.readTree(next.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("memory").path("imageUrl").asText();
        assertNotEquals(firstImage, nextImage, "照片牌库用完前不应重复同一段回忆");
    }

    @Test
    void memoryGuessRequiresTwoPhotoMemories() throws Exception {
        mvc.perform(post("/api/games").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"MEMORY_GUESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMORY_GAME_NEEDS_MORE_MEMORIES"));

        createMemory("只有一张照片", "还需要再收藏一张。",
                LocalDateTime.of(2025, 3, 1, 12, 0), "家里", true);
        createMemory("只有视频", "视频不会作为猜图卡面。",
                LocalDateTime.of(2025, 4, 1, 12, 0), "公园", false);
        mvc.perform(post("/api/games").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"MEMORY_GUESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMORY_GAME_NEEDS_PHOTOS"));
    }

    @Test
    void truthCardsAlternateTurnsAndRejectStaleRoundRequests() throws Exception {
        long gameId = createGame(alice, "TRUTH_CARD");
        JsonNode first = getGame(alice, gameId);
        String firstPrompt = first.path("prompt").asText();
        org.junit.jupiter.api.Assertions.assertFalse(firstPrompt.isBlank());
        org.junit.jupiter.api.Assertions.assertFalse(first.path("cardCategory").asText().isBlank());
        org.junit.jupiter.api.Assertions.assertEquals(aliceId.longValue(), first.path("currentTurnUserId").asLong());

        mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(bob)
                        .queryParam("roundNumber", "1"))
                .andExpect(status().isForbidden());
        MvcResult secondResult = mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(alice)
                        .queryParam("roundNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.currentTurnUserId").value(bobId))
                .andReturn();
        String secondPrompt = objectMapper.readTree(secondResult.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("prompt").asText();
        assertNotEquals(firstPrompt, secondPrompt, "题库用完前不应连续抽到同一张真心话");

        mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(bob)
                        .queryParam("roundNumber", "1"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/games/{id}", gameId).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.prompt").value(secondPrompt));
    }

    @Test
    void drawingAndGuessingRespectRolesAndSwitchEachRound() throws Exception {
        long gameId = createGame(alice, "DRAW_GUESS");
        String firstWord = getGame(alice, gameId).get("secretWord").asText();

        mvc.perform(get("/api/games/{id}", gameId).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTurnUserId").value(aliceId))
                .andExpect(jsonPath("$.secretWord").value(firstWord));
        mvc.perform(get("/api/games/{id}", gameId).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretWord").value(nullValue()));

        String stroke = """
                {"roundNumber":1,"operationId":"stroke-1","strokes":[{"color":"#c95868","width":5,
                "points":[{"x":0.1,"y":0.2},{"x":0.3,"y":0.4}]}]}
                """;
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content(stroke))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(stroke))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strokes", hasSize(1)))
                .andExpect(jsonPath("$.strokes[0].tool").value("DRAW"));
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(stroke))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strokes", hasSize(1)));

        String eraserStroke = """
                {"roundNumber":1,"operationId":"stroke-2","strokes":[{"tool":"ERASE","color":"#c95868","width":16,
                "points":[{"x":0.2,"y":0.3},{"x":0.4,"y":0.5}]}]}
                """;
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(eraserStroke))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strokes", hasSize(2)))
                .andExpect(jsonPath("$.strokes[1].tool").value("ERASE"));

        String longPoints = java.util.stream.IntStream.range(0, 480)
                .mapToObj(index -> "{\"x\":" + (index % 100) / 100.0
                        + ",\"y\":" + (index % 80) / 80.0 + "}")
                .collect(java.util.stream.Collectors.joining(","));
        String longStroke = "{\"roundNumber\":1,\"operationId\":\"stroke-3\",\"strokes\":[{\"tool\":\"DRAW\",\"color\":\"#374151\","
                + "\"width\":4,\"points\":[" + longPoints + "]}]}";
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(longStroke))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strokes", hasSize(3)));

        mvc.perform(post("/api/games/{id}/guess", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("guess", firstWord))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/games/{id}/guess", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guess\":\"这肯定不是题库答案\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roundComplete").value(false));
        mvc.perform(post("/api/games/{id}/guess", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("guess", firstWord))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundComplete").value(true))
                .andExpect(jsonPath("$.secretWord").value(firstWord))
                .andExpect(jsonPath("$.score").value(1));

        MvcResult nextRoundResult = mvc.perform(post("/api/games/{id}/next", gameId).with(csrf()).session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(2))
                .andExpect(jsonPath("$.strokes", hasSize(0)))
                .andReturn();
        String nextWord = objectMapper.readTree(nextRoundResult.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("secretWord").asText();
        assertNotEquals(firstWord, nextWord, "相邻两轮不应抽到同一个你画我猜词语");

        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON).content(stroke))
                .andExpect(status().isForbidden());
        String staleRound = stroke.replace("\"roundNumber\":1", "\"roundNumber\":2")
                .replace("\"operationId\":\"stroke-1\"", "\"operationId\":\"stale-round\"");
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON).content(staleRound))
                .andExpect(status().isOk());
        mvc.perform(post("/api/games/{id}/strokes", gameId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleRound.replace("\"roundNumber\":2", "\"roundNumber\":1")
                                .replace("\"operationId\":\"stale-round\"", "\"operationId\":\"old-round\"")))
                .andExpect(status().isConflict());
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

    private JsonNode getGame(MockHttpSession session, long gameId) throws Exception {
        String response = mvc.perform(get("/api/games/{id}", gameId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private Memory createMemory(String title, String description, LocalDateTime eventAt,
                                String location, boolean image) {
        Memory memory = new Memory();
        memory.setCoupleId(couple.getId());
        memory.setAuthorId(aliceId);
        memory.setTitle(title);
        memory.setDescription(description);
        memory.setEventAt(eventAt);
        memory.setLocation(location);
        Memory saved = memories.save(memory);

        Media attachment = new Media();
        attachment.setCoupleId(couple.getId());
        attachment.setOwnerId(aliceId);
        attachment.setMemoryId(saved.getId());
        attachment.setStoredName("game-test-" + saved.getId() + (image ? ".jpg" : ".mp4"));
        attachment.setOriginalName(image ? "memory.jpg" : "memory.mp4");
        attachment.setContentType(image ? "image/jpeg" : "video/mp4");
        attachment.setMediaType(image ? "image" : "video");
        attachment.setByteSize(128);
        media.save(attachment);
        return saved;
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
