package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lovespace.domain.Couple;
import com.lovespace.domain.User;
import com.lovespace.domain.Wish;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.repository.WishRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class WishConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;

    private Long coupleId;
    private Long wishId;

    @BeforeEach
    void resetAndCreateWish() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Couple couple = new Couple();
        couple.setSpaceName("愿望并发测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        coupleId = couples.saveAndFlush(couple).getId();
        User alice = users.saveAndFlush(user(couple, "alice", "小爱"));
        // Keep both users in the couple so this test mirrors the two-client scope.
        if (alice == null) throw new IllegalStateException("failed to create test user");
        users.saveAndFlush(user(couple, "bob", "小宝"));

        Wish wish = new Wish();
        wish.setCoupleId(coupleId);
        wish.setCreatedBy(alice.getId());
        wish.setTitle("并发前的愿望");
        wish.setCategory("OTHER");
        wish.setStatus(Wish.STATUS_ACTIVE);
        wishId = wishes.saveAndFlush(wish).getId();
    }

    @Test
    void simultaneousFullUpdatesRejectOneSnapshotAndKeepTheOther() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> updateWish(bothLoaded, release, "设备 A 的内容"));
            Future<?> second = executor.submit(() -> updateWish(bothLoaded, release, "设备 B 的内容"));
            assertTrue(bothLoaded.await(5, TimeUnit.SECONDS));
            release.countDown();

            Throwable firstError = failure(first);
            Throwable secondError = failure(second);
            assertTrue((firstError == null) ^ (secondError == null),
                    "两个同版本事务中必须恰好一个提交");
            Throwable staleError = firstError == null ? secondError : firstError;
            assertTrue(isOptimisticFailure(staleError), "失败事务必须是乐观锁冲突");

            String finalTitle = wishes.findById(wishId).orElseThrow().getTitle();
            assertTrue(finalTitle.equals("设备 A 的内容") || finalTitle.equals("设备 B 的内容"));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void updateWish(CountDownLatch bothLoaded, CountDownLatch release, String title) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Wish wish = wishes.findById(wishId).orElseThrow();
            bothLoaded.countDown();
            await(release);
            wish.setTitle(title);
            wishes.saveAndFlush(wish);
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
