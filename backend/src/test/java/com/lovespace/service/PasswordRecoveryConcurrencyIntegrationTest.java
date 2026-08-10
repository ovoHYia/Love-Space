package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.lovespace.api.dto.ApiDtos.PasswordChangeRequest;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.Couple;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.security.SessionPrincipal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
class PasswordRecoveryConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
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
        couple.setSpaceName("并发测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        coupleId = couples.saveAndFlush(couple).getId();
        User user = new User();
        user.setCouple(couple);
        user.setUsername("alice");
        user.setNickname("小爱");
        user.setPasswordHash(encoder.encode("initial-pass-123"));
        userId = users.saveAndFlush(user).getId();
    }

    @Test
    void recoveryWinsWhenOlderChangeTransactionCommitsFirst() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch changeWritten = new CountDownLatch(1);
        CountDownLatch releaseChange = new CountDownLatch(1);
        try {
            Future<?> change = executor.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    accounts.changePassword(authentication(),
                            new PasswordChangeRequest("initial-pass-123", "attacker-pass-123"));
                    changeWritten.countDown();
                    await(releaseChange);
                });
            });
            assertTrue(changeWritten.await(5, TimeUnit.SECONDS));

            Future<?> recovery = executor.submit(() -> accounts.resetPassword("alice", "recovery-pass-123"));
            assertFutureWaitsForLock(recovery, "recovery must wait for the old change transaction");
            releaseChange.countDown();
            change.get(5, TimeUnit.SECONDS);
            recovery.get(5, TimeUnit.SECONDS);

            User finalUser = users.findById(userId).orElseThrow();
            assertTrue(encoder.matches("recovery-pass-123", finalUser.getPasswordHash()));
            assertFalse(encoder.matches("attacker-pass-123", finalUser.getPasswordHash()));
            assertTrue(finalUser.getPasswordVersion() >= 2);
        } finally {
            releaseChange.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void oldChangeFailsAfterRecoveryCommitsFirst() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch recoveryWritten = new CountDownLatch(1);
        CountDownLatch releaseRecovery = new CountDownLatch(1);
        try {
            Future<?> recovery = executor.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    accounts.resetPassword("alice", "recovery-pass-456");
                    recoveryWritten.countDown();
                    await(releaseRecovery);
                });
            });
            assertTrue(recoveryWritten.await(5, TimeUnit.SECONDS));

            Future<?> change = executor.submit(() -> accounts.changePassword(authentication(),
                    new PasswordChangeRequest("initial-pass-123", "attacker-pass-456")));
            assertFutureWaitsForLock(change, "old change must wait for the recovery transaction");
            releaseRecovery.countDown();
            recovery.get(5, TimeUnit.SECONDS);
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> change.get(5, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof ApiException);
            assertTrue(((ApiException) error.getCause()).getStatus().is4xxClientError());

            User finalUser = users.findById(userId).orElseThrow();
            assertTrue(encoder.matches("recovery-pass-456", finalUser.getPasswordHash()));
            assertFalse(encoder.matches("attacker-pass-456", finalUser.getPasswordHash()));
            assertTrue(finalUser.getPasswordVersion() >= 1);
        } finally {
            releaseRecovery.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Authentication authentication() {
        User user = users.findById(userId).orElseThrow();
        SessionPrincipal principal = new SessionPrincipal(user.getId(), coupleId, user.getUsername(),
                user.getPasswordHash(), user.getPasswordVersion());
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    private void assertFutureWaitsForLock(Future<?> future, String message) throws Exception {
        try {
            future.get(1, TimeUnit.SECONDS);
            fail(message);
        } catch (TimeoutException expected) {
            // The first transaction still owns the PESSIMISTIC_WRITE row lock.
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", ex);
        }
    }
}
