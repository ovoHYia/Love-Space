package com.lovespace;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lovespace.domain.*;
import com.lovespace.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired AnniversaryRepository anniversaries;
    @Autowired MemoryRepository memories;
    @Autowired DiaryRepository diaries;
    @Autowired WishRepository wishes;
    @Autowired LetterMessageRepository messages;
    @Autowired PasswordEncoder encoder;

    private Long coupleId;
    private Long aliceId;
    private Long bobId;
    private MockHttpSession alice;
    private MockHttpSession bob;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"notification_preferences", "notifications", "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories", "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Couple couple = new Couple();
        couple.setSpaceName("我们的小时光");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.save(couple);
        coupleId = couple.getId();
        aliceId = users.save(user(couple, "alice", "小爱", "alice-pass-123")).getId();
        bobId = users.save(user(couple, "bob", "小宝", "bob-pass-123")).getId();
        alice = login("alice", "alice-pass-123");
        bob = login("bob", "bob-pass-123");
    }

    @Test
    void calendarAggregatesSourcesAndProtectsFutureLetters() throws Exception {
        YearMonth targetMonth = YearMonth.now(ZoneId.of("Asia/Shanghai")).plusMonths(1);
        LocalDate day3 = targetMonth.atDay(3);
        LocalDate day4 = targetMonth.atDay(4);
        LocalDate day5 = targetMonth.atDay(5);
        LocalDate day6 = targetMonth.atDay(6);
        LocalDate day7 = targetMonth.atDay(7);
        LocalDate day8 = targetMonth.atDay(8);
        String from = targetMonth.atDay(1).toString();
        String to = targetMonth.atEndOfMonth().toString();

        Anniversary anniversary = new Anniversary();
        anniversary.setCoupleId(coupleId);
        anniversary.setCreatedBy(aliceId);
        anniversary.setTitle("相识纪念日");
        anniversary.setEventDate(day3.minusYears(1));
        anniversary.setType("LOVE");
        anniversary.setRecurringYearly(true);
        anniversary.setReminderDays(7);
        anniversaries.save(anniversary);

        Memory memory = new Memory();
        memory.setCoupleId(coupleId);
        memory.setAuthorId(aliceId);
        memory.setTitle("夏日海边");
        memory.setEventAt(day4.atTime(18, 30));
        memories.save(memory);

        Diary diary = new Diary();
        diary.setCoupleId(coupleId);
        diary.setAuthorId(bobId);
        diary.setTitle("八月日记");
        diary.setContent("一起散步");
        diary.setDiaryDate(day5);
        diaries.save(diary);

        Wish wish = new Wish();
        wish.setCoupleId(coupleId);
        wish.setCreatedBy(aliceId);
        wish.setTitle("去看极光");
        wish.setCategory("TRAVEL");
        wish.setStatus(Wish.STATUS_ACTIVE);
        wish.setTargetDate(day6);
        wishes.save(wish);

        LetterMessage letter = new LetterMessage();
        letter.setCoupleId(coupleId);
        letter.setAuthorId(aliceId);
        letter.setRecipientId(bobId);
        letter.setContent("未来的惊喜");
        letter.setScheduled(true);
        letter.setDeliverAt(day7.atTime(8, 0));
        messages.save(letter);

        String created = mvc.perform(post("/api/calendar/events").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"周末看展","description":"提前买票",
                                 "startAt":"%sT14:00:00","endAt":"%sT17:00:00",
                                 "allDay":false,"category":"DATE","location":"美术馆"}
                                """.formatted(day8, day8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("CUSTOM"))
                .andExpect(jsonPath("$.editable").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long eventId = idOf(created);

        mvc.perform(get("/api/calendar").session(alice)
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].sourceType", containsInAnyOrder(
                        "CUSTOM", "ANNIVERSARY", "MEMORY", "DIARY", "WISH", "LETTER")))
                .andExpect(jsonPath("$[?(@.sourceType == 'ANNIVERSARY')].startAt",
                        contains(day3 + "T00:00:00")));
        mvc.perform(get("/api/calendar").session(bob)
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].sourceType", not(hasItem("LETTER"))));

        mvc.perform(put("/api/calendar/events/{id}", eventId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"周末看新展","startAt":"%sT15:00:00",
                                 "allDay":false,"category":"DATE","location":"新美术馆"}
                                """.formatted(day8)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("周末看新展"));
        mvc.perform(delete("/api/calendar/events/{id}", eventId).with(csrf()).session(bob))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/trash").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CALENDAR_EVENT"));
        mvc.perform(get("/api/trash").session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/trash/CALENDAR_EVENT/{id}/restore", eventId).with(csrf()).session(bob))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/calendar").session(alice)
                        .param("from", day8.toString()).param("to", day8.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("周末看新展"));
    }

    @Test
    void calendarValidatesRangesAndEventTimes() throws Exception {
        mvc.perform(get("/api/calendar").session(alice)
                        .param("from", "2026-01-01").param("to", "2027-12-31"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/calendar/events").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"时间错误","startAt":"2026-08-08T18:00:00",
                                 "endAt":"2026-08-08T17:00:00","allDay":false,"category":"OTHER"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
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
}
