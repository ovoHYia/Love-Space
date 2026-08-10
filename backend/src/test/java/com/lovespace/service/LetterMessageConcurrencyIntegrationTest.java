package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lovespace.domain.Couple;
import com.lovespace.domain.LetterMessage;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.LetterMessageRepository;
import com.lovespace.repository.UserRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;

@SpringBootTest
@ActiveProfiles("test")
class LetterMessageConcurrencyIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired LetterMessageRepository messages;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;

    private Long coupleId;
    private Long aliceId;
    private Long bobId;
    private Long messageId;

    @BeforeEach
    void resetAndCreateMessage() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Couple couple = new Couple();
        couple.setSpaceName("信笺并发测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        coupleId = couples.saveAndFlush(couple).getId();
        aliceId = users.saveAndFlush(user(couple, "alice", "小爱")).getId();
        bobId = users.saveAndFlush(user(couple, "bob", "小宝")).getId();

        LetterMessage message = new LetterMessage();
        message.setCoupleId(coupleId);
        message.setAuthorId(aliceId);
        message.setRecipientId(bobId);
        message.setContent("并发测试信");
        message.setScheduled(false);
        message.setDeliverAt(LocalDateTime.now(ZONE).minusMinutes(1));
        messageId = messages.saveAndFlush(message).getId();
    }

    @Test
    void concurrentReadAndDeleteCannotOverwriteEachOther() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> read = executor.submit(() -> updateReadAt(bothLoaded, release));
            Future<?> delete = executor.submit(() -> moveToTrash(bothLoaded, release));
            assertTrue(bothLoaded.await(5, TimeUnit.SECONDS));
            release.countDown();

            Throwable readError = failure(read);
            Throwable deleteError = failure(delete);
            assertTrue((readError == null) ^ (deleteError == null),
                    "exactly one stale snapshot operation should commit");
            Throwable staleError = readError == null ? deleteError : readError;
            assertTrue(isOptimisticFailure(staleError), "the losing operation must be a stale-data conflict");

            LetterMessage finalValue = messages.findById(messageId).orElseThrow();
            assertTrue((finalValue.getReadAt() == null) ^ (finalValue.getDeletedAt() == null),
                    "the committed operation must not erase the other operation's state");
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void updateReadAt(CountDownLatch bothLoaded, CountDownLatch release) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LetterMessage value = messages.findByIdAndCoupleIdAndDeletedAtIsNull(messageId, coupleId).orElseThrow();
            bothLoaded.countDown();
            await(release);
            value.setReadAt(LocalDateTime.now(ZONE));
            messages.saveAndFlush(value);
        });
    }

    private void moveToTrash(CountDownLatch bothLoaded, CountDownLatch release) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LetterMessage value = messages.findByIdAndCoupleIdAndDeletedAtIsNull(messageId, coupleId).orElseThrow();
            bothLoaded.countDown();
            await(release);
            value.moveToTrash(aliceId, LocalDateTime.now(ZONE));
            messages.saveAndFlush(value);
        });
    }

    private Throwable failure(Future<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException ex) {
            return ex.getCause();
        }
    }

    private boolean isOptimisticFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof OptimisticLockException
                    || current instanceof OptimisticLockingFailureException) return true;
        }
        return false;
    }

    private User user(Couple couple, String username, String nickname) {
        User user = new User();
        user.setCouple(couple);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash(encoder.encode(username + "-pass-123"));
        return user;
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
