package com.lovespace;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest(properties = "app.security.setup.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupDisabledStatusIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void resetAndSeed() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) jdbc.execute("TRUNCATE TABLE " + table);
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        Couple couple = new Couple();
        couple.setSpaceName("已初始化空间");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.save(couple);
        User user = new User();
        user.setCouple(couple);
        user.setUsername("setup-user");
        user.setNickname("已登录");
        user.setPasswordHash(encoder.encode("setup-user-pass"));
        users.save(user);
        User partner = new User();
        partner.setCouple(couple);
        partner.setUsername("setup-partner");
        partner.setNickname("伴侣");
        partner.setPasswordHash(encoder.encode("setup-partner-pass"));
        users.save(partner);
    }

    @Test
    void statusRemainsPublicButInitializeRemainsDisabledAndSessionCanRefresh() throws Exception {
        mvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.initialized", equalTo(true)));
        mvc.perform(post("/api/setup/initialize").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceName\":\"新空间\",\"loveStartedAt\":\"2025-02-14T20:00:00\","
                                + "\"firstUser\":{\"username\":\"first-user\",\"password\":\"first-pass-123\",\"nickname\":\"甲\"},"
                                + "\"secondUser\":{\"username\":\"second-user\",\"password\":\"second-pass-123\",\"nickname\":\"乙\"}}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SETUP_DISABLED"));

        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "setup-user").param("password", "setup-user-pass"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("setup-user"));
    }
}
