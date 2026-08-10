package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lovespace.api.dto.ApiDtos.MoodRequest;
import com.lovespace.domain.Couple;
import com.lovespace.domain.Mood;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.MoodRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.security.SessionPrincipal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class MoodConcurrencyIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired MoodRepository moods;
    @Autowired AccountService accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;

    private Long userId;
    private Long coupleId;

    @BeforeEach
    void resetAndCreateUser() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Couple couple = new Couple();
        couple.setSpaceName("心情并发测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        coupleId = couples.saveAndFlush(couple).getId();
        User user = new User();
        user.setCouple(couple);
        user.setUsername("alice");
        user.setNickname("小爱");
        user.setPasswordHash(encoder.encode("alice-pass-123"));
        userId = users.saveAndFlush(user).getId();
    }

    @Test
    void concurrentFirstWritesForOneUserSerializeWithoutConflict() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<?> first = executor.submit(() -> writeMood(start, "开心"));
            Future<?> second = executor.submit(() -> writeMood(start, "期待"));

            assertDoesNotThrow(() -> first.get(10, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> second.get(10, TimeUnit.SECONDS));

            Mood result = moods.findByUserIdAndMoodDate(userId, LocalDate.now(ZONE)).orElseThrow();
            assertEquals(userId, result.getUserId());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void writeMood(CyclicBarrier start, String label) {
        await(start);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                accounts.setTodayMood(authentication(), new MoodRequest("😊", label, null)));
    }

    private Authentication authentication() {
        User user = users.findById(userId).orElseThrow();
        SessionPrincipal principal = new SessionPrincipal(user.getId(), coupleId, user.getUsername(),
                user.getPasswordHash(), user.getPasswordVersion());
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("test synchronization failed", ex);
        }
    }
}
