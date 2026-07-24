package com.lovespace;

import static org.hamcrest.Matchers.hasSize;
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
class WishIntegrationTest {
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
        for (String table : new String[]{"memory_tags", "notification_preferences", "notifications", "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories", "moods", "users", "couples"}) {
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
    void coupleCanCreateCompleteReopenAndDeleteWish() throws Exception {
        String response = mvc.perform(post("/api/wishes").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"一起去看极光","description":"在下雪的地方抱紧彼此",
                                 "category":"TRAVEL","targetDate":"2028-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdByNickname").value("小爱"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long wishId = idOf(response);

        mvc.perform(get("/api/wishes").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("一起去看极光"));
        mvc.perform(get("/api/notifications").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("WISH_CREATED"))
                .andExpect(jsonPath("$.items[0].referenceType").value("WISH"));

        mvc.perform(patch("/api/wishes/{id}/complete", wishId).with(csrf()).session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedByNickname").value("小宝"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        mvc.perform(get("/api/notifications").session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("WISH_COMPLETED"))
                .andExpect(jsonPath("$.items[0].referenceId").value(wishId));

        mvc.perform(patch("/api/wishes/{id}/reopen", wishId).with(csrf()).session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        mvc.perform(put("/api/wishes/{id}", wishId).with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"一起去冰岛看极光","description":"认真攒旅行基金",
                                 "category":"TRAVEL","targetDate":"2029-01-01"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("一起去冰岛看极光"));
        mvc.perform(delete("/api/wishes/{id}", wishId).with(csrf()).session(alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/wishes").session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/wishes").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"无效分类\",\"category\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void wishesAreCoupleScoped() throws Exception {
        String response = mvc.perform(post("/api/wishes").with(csrf()).session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"只属于我们的愿望\",\"category\":\"OTHER\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long wishId = idOf(response);

        Couple other = new Couple();
        other.setSpaceName("另一间小屋");
        other.setLoveStartedAt(LocalDateTime.now().minusDays(10));
        couples.save(other);
        newUser(other, "outsider", "访客", "outsider-pass-123");
        newUser(other, "outsider_partner", "访客伴侣", "partner-pass-123");
        MockHttpSession outsider = login("outsider", "outsider-pass-123");

        mvc.perform(get("/api/wishes").session(outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(patch("/api/wishes/{id}/complete", wishId).with(csrf()).session(outsider))
                .andExpect(status().isNotFound());
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
