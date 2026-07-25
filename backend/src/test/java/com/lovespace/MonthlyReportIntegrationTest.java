package com.lovespace;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lovespace.domain.*;
import com.lovespace.repository.*;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonthlyReportIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MoodRepository moods;
    @Autowired MemoryRepository memories;
    @Autowired DiaryRepository diaries;
    @Autowired LetterMessageRepository messages;
    @Autowired WishRepository wishes;
    @Value("${SETUP_TOKEN}") String setupToken;

    private MockHttpSession firstSession;
    private User alice;
    private User bob;
    private YearMonth reportMonth;

    @BeforeEach
    void resetAndInitialize() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications", "calendar_events", "wishes", "anniversaries",
                "messages", "diaries", "media", "memories", "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        String setup = """
                {"spaceName":"我们的小时光","loveStartedAt":"2025-02-14T20:00:00",
                 "firstUser":{"username":"alice","password":"alice-pass-123","nickname":"小爱"},
                 "secondUser":{"username":"bob","password":"bob-pass-123","nickname":"小宝"}}
                """;
        mvc.perform(post("/api/setup/initialize").with(csrf()).header("X-Setup-Token", setupToken)
                        .contentType(MediaType.APPLICATION_JSON).content(setup))
                .andExpect(status().isCreated());
        firstSession = login("alice", "alice-pass-123");
        alice = users.findByUsernameIgnoreCase("alice").orElseThrow();
        bob = users.findByUsernameIgnoreCase("bob").orElseThrow();
        reportMonth = YearMonth.now(ZONE).minusMonths(1);
    }

    @Test
    void monthlyReportAggregatesMoodTrendAndCoupleActivities() throws Exception {
        LocalDate first = reportMonth.atDay(1);
        saveMood(alice, first, "😊", "开心");
        saveMood(bob, first, "🥰", "甜甜的");
        saveMood(alice, first.plusDays(1), "😌", "平静");
        saveMood(bob, first.plusDays(3), "😴", "有点累");

        Memory memory = new Memory();
        memory.setCoupleId(alice.getCouple().getId());
        memory.setAuthorId(alice.getId());
        memory.setTitle("一起看晚霞");
        memory.setEventAt(first.plusDays(6).atTime(18, 30));
        memories.save(memory);

        Diary diary = new Diary();
        diary.setCoupleId(alice.getCouple().getId());
        diary.setAuthorId(bob.getId());
        diary.setTitle("周末散步");
        diary.setContent("沿着河边慢慢走。");
        diary.setDiaryDate(first.plusDays(7));
        diary.setMood("温柔");
        diaries.save(diary);

        LetterMessage letter = new LetterMessage();
        letter.setCoupleId(alice.getCouple().getId());
        letter.setAuthorId(alice.getId());
        letter.setRecipientId(bob.getId());
        letter.setContent("谢谢你一直都在。");
        letter.setScheduled(false);
        letter.setDeliverAt(first.plusDays(8).atTime(20, 0));
        messages.save(letter);

        Wish wish = new Wish();
        wish.setCoupleId(alice.getCouple().getId());
        wish.setCreatedBy(alice.getId());
        wish.setTitle("一起做蛋糕");
        wish.setCategory("FOOD");
        wish.setStatus(Wish.STATUS_COMPLETED);
        wish.setCompletedBy(bob.getId());
        wish.setCompletedAt(first.plusDays(9).atTime(16, 0));
        wishes.save(wish);

        mvc.perform(get("/api/reports/monthly").session(firstSession)
                        .param("month", reportMonth.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(reportMonth.toString()))
                .andExpect(jsonPath("$.totalMoodEntries").value(4))
                .andExpect(jsonPath("$.recordedDays").value(3))
                .andExpect(jsonPath("$.sharedMoodDays").value(1))
                .andExpect(jsonPath("$.longestStreak").value(2))
                .andExpect(jsonPath("$.resonanceRate").value(100))
                .andExpect(jsonPath("$.trend.length()").value(4))
                .andExpect(jsonPath("$.trend[0].nickname").value("小爱"))
                .andExpect(jsonPath("$.trend[0].score").value(5))
                .andExpect(jsonPath("$.people.length()").value(2))
                .andExpect(jsonPath("$.people[0].recordedDays").value(2))
                .andExpect(jsonPath("$.distribution[0].percentage").value(25))
                .andExpect(jsonPath("$.activities.memories").value(1))
                .andExpect(jsonPath("$.activities.diaries").value(1))
                .andExpect(jsonPath("$.activities.letters").value(1))
                .andExpect(jsonPath("$.activities.completedWishes").value(1))
                .andExpect(jsonPath("$.highlights.length()").value(4));
    }

    @Test
    void monthlyReportRejectsMalformedAndFutureMonths() throws Exception {
        mvc.perform(get("/api/reports/monthly").session(firstSession).param("month", "2026/07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/reports/monthly").session(firstSession)
                        .param("month", YearMonth.now(ZONE).plusMonths(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("暂不能生成未来月份的报告"));
    }

    private void saveMood(User user, LocalDate date, String emoji, String label) {
        Mood mood = new Mood();
        mood.setCoupleId(user.getCouple().getId());
        mood.setUserId(user.getId());
        mood.setMoodDate(date);
        mood.setEmoji(emoji);
        mood.setLabel(label);
        moods.save(mood);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username).param("password", password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
