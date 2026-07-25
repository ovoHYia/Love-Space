package com.lovespace;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationReminderTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired AnniversaryRepository anniversaries;
    @Autowired LetterMessageRepository letterMessages;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationService notificationService;
    @Autowired PasswordEncoder encoder;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private Long coupleId;
    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void reset() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications", "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories", "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Couple couple = new Couple();
        couple.setSpaceName("我们的小时光");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.save(couple);
        coupleId = couple.getId();
        aliceId = newUser(couple, "alice", "小爱", "alice-pass-123").getId();
        bobId = newUser(couple, "bob", "小宝", "bob-pass-123").getId();
    }

    @Test
    void generatesOneReminderPerMemberAndIsIdempotent() {
        LocalDate today = LocalDate.now(ZONE);
        newAnniversary(today.plusDays(3), 7);    // within the reminder window
        newAnniversary(today.plusDays(30), 7);   // outside the reminder window
        Anniversary deleted = newAnniversary(today.plusDays(2), 7);
        deleted.moveToTrash(aliceId, LocalDateTime.now(ZONE));
        anniversaries.save(deleted);

        assertEquals(2, notificationService.generateAnniversaryReminders(today), "两位成员应各生成一条");
        assertEquals(0, notificationService.generateAnniversaryReminders(today), "重复运行不应再生成");
        assertEquals(2, notifications.count(), "窗口外的纪念日不应生成通知");
        assertEquals(1, notifications.countByUserIdAndReadAtIsNull(aliceId));
    }

    @Test
    void notificationApiListsMarksReadAndCounts() throws Exception {
        newAnniversary(LocalDate.now(ZONE).plusDays(1), 7);
        notificationService.generateAnniversaryReminders(LocalDate.now(ZONE));

        MockHttpSession alice = login("alice", "alice-pass-123");
        String body = mvc.perform(get("/api/notifications").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("ANNIVERSARY_REMINDER"))
                .andExpect(jsonPath("$.items[0].referenceType").value("ANNIVERSARY"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = idOf(body);
        mvc.perform(patch("/api/notifications/{id}/read", id).with(csrf()).session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").isNotEmpty());
        mvc.perform(get("/api/notifications/unread-count").session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));

        // Read state is per-user: bob's own reminder is still unread until he clears it.
        MockHttpSession bob = login("bob", "bob-pass-123");
        mvc.perform(get("/api/notifications/unread-count").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(post("/api/notifications/read-all").with(csrf()).session(bob))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void notificationCenterFiltersPagesAndPerformsBatchActions() throws Exception {
        Notification anniversary = newNotification(aliceId, "ANNIVERSARY_REMINDER", "纪念日提醒", "ANNIVERSARY");
        Notification letter = newNotification(aliceId, "TIME_CAPSULE_DELIVERED", "时光信到了", "MESSAGE");
        letter.setReadAt(LocalDateTime.now(ZONE));
        notifications.save(letter);
        Notification wish = newNotification(aliceId, "WISH_CREATED", "新的愿望", "WISH");

        MockHttpSession alice = login("alice", "alice-pass-123");
        mvc.perform(get("/api/notifications").session(alice)
                        .param("status", "UNREAD").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.unread").value(2));
        mvc.perform(get("/api/notifications").session(alice)
                        .param("category", "WISH").param("keyword", "愿望"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(wish.getId()));

        mvc.perform(post("/api/notifications/batch/read").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + anniversary.getId() + "," + wish.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(2))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(post("/api/notifications/batch/unread").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + anniversary.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        MockHttpSession bob = login("bob", "bob-pass-123");
        mvc.perform(delete("/api/notifications/batch").with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + anniversary.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(0));
        mvc.perform(delete("/api/notifications/{id}", letter.getId()).with(csrf()).session(alice))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/notifications/read").with(csrf()).session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(1))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void notificationPreferencesControlFutureNotificationCreation() throws Exception {
        MockHttpSession alice = login("alice", "alice-pass-123");
        mvc.perform(get("/api/notifications/preferences").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anniversaryEnabled").value(true))
                .andExpect(jsonPath("$.letterEnabled").value(true))
                .andExpect(jsonPath("$.wishEnabled").value(true));
        mvc.perform(put("/api/notifications/preferences").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anniversaryEnabled":false,"letterEnabled":true,"wishEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anniversaryEnabled").value(false))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        LocalDate today = LocalDate.now(ZONE);
        newAnniversary(today.plusDays(1), 7);
        assertEquals(1, notificationService.generateAnniversaryReminders(today));
        assertEquals(0, notifications.countByUserId(aliceId));
        assertEquals(1, notifications.countByUserId(bobId));
    }

    @Test
    void notificationsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void scheduledLetterIsHiddenUntilDelivery() throws Exception {
        MockHttpSession alice = login("alice", "alice-pass-123");
        MockHttpSession bob = login("bob", "bob-pass-123");
        LocalDateTime deliverAt = LocalDateTime.now(ZONE).plusHours(2).withNano(0);

        String response = mvc.perform(post("/api/messages").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"两小时后的惊喜\",\"deliverAt\":\"" + deliverAt + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduled").value(true))
                .andExpect(jsonPath("$.content").value("两小时后的惊喜"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long messageId = idOf(response);

        mvc.perform(get("/api/messages").session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content").value("两小时后的惊喜"));
        mvc.perform(get("/api/messages").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(patch("/api/messages/{id}/read", messageId).with(csrf()).session(bob))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/messages").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"不能寄往过去\",\"deliverAt\":\""
                                + LocalDateTime.now(ZONE).minusMinutes(1).withNano(0) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void deliveredTimeCapsuleCreatesOneNotificationAndCanBeOpened() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZONE).withNano(0);
        LetterMessage capsule = new LetterMessage();
        capsule.setCoupleId(coupleId); capsule.setAuthorId(aliceId); capsule.setRecipientId(bobId);
        capsule.setContent("来自过去的一封信"); capsule.setScheduled(true);
        capsule.setDeliverAt(now.minusMinutes(1));
        capsule = letterMessages.save(capsule);

        assertEquals(1, notificationService.generateScheduledLetterNotifications(now));
        assertEquals(0, notificationService.generateScheduledLetterNotifications(now));
        assertNotNull(letterMessages.findById(capsule.getId()).orElseThrow().getNotifiedAt());

        MockHttpSession bob = login("bob", "bob-pass-123");
        mvc.perform(get("/api/notifications").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TIME_CAPSULE_DELIVERED"))
                .andExpect(jsonPath("$.items[0].referenceType").value("MESSAGE"))
                .andExpect(jsonPath("$.items[0].referenceId").value(capsule.getId()));
        mvc.perform(get("/api/messages").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].scheduled").value(true))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
        mvc.perform(patch("/api/messages/{id}/read", capsule.getId()).with(csrf()).session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("来自过去的一封信"))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    private User newUser(Couple couple, String username, String nickname, String password) {
        User user = new User();
        user.setCouple(couple); user.setUsername(username); user.setNickname(nickname);
        user.setPasswordHash(encoder.encode(password));
        return users.save(user);
    }
    private Anniversary newAnniversary(LocalDate date, int reminderDays) {
        Anniversary value = new Anniversary();
        value.setCoupleId(coupleId); value.setCreatedBy(aliceId); value.setTitle("恋爱纪念日");
        value.setEventDate(date); value.setType("LOVE"); value.setRecurringYearly(false);
        value.setReminderDays(reminderDays);
        return anniversaries.save(value);
    }
    private Notification newNotification(Long userId, String type, String title, String referenceType) {
        Notification value = new Notification();
        value.setCoupleId(coupleId);
        value.setUserId(userId);
        value.setType(type);
        value.setTitle(title);
        value.setBody(title + "的详细内容");
        value.setReferenceType(referenceType);
        value.setDedupeKey("TEST:" + type + ":" + title);
        return notifications.save(value);
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
