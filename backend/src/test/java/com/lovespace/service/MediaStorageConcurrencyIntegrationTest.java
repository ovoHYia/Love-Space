package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.*;

import com.lovespace.api.error.ApiException;
import com.lovespace.domain.Couple;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.MediaRepository;
import com.lovespace.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "app.upload-dir=${java.io.tmpdir}/love-space-concurrent-upload-test",
        "app.media-max-bytes=1024",
        "app.media-total-max-bytes=20",
        "app.media-min-free-bytes=0"
})
@ActiveProfiles("test")
class MediaStorageConcurrencyIntegrationTest {
    @Autowired MediaStorageService storage;
    @Autowired MediaRepository media;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Value("${app.upload-dir}") String uploadDir;

    private Long ownerId;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media",
                "memories", "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        clearUploadDirectory();

        Couple couple = new Couple();
        couple.setSpaceName("并发配额测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.save(couple);
        User owner = new User();
        owner.setCouple(couple);
        owner.setUsername("quota-owner");
        owner.setNickname("配额测试");
        owner.setPasswordHash(encoder.encode("quota-owner-pass"));
        ownerId = users.save(owner).getId();
    }

    @AfterEach
    void cleanup() throws Exception {
        clearUploadDirectory();
    }

    @Test
    void concurrentUploadsCannotOversubscribeCoupleQuota() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> upload = () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        User owner = users.findById(ownerId).orElseThrow();
                        storage.store(owner, null, png());
                    });
                    return "STORED";
                } catch (ApiException ex) {
                    return ex.getCode();
                }
            };
            Future<String> first = executor.submit(upload);
            Future<String> second = executor.submit(upload);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<String> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertTrue(outcomes.contains("STORED"));
            assertTrue(outcomes.contains("STORAGE_QUOTA_EXCEEDED"));
            assertEquals(1, media.count());
            try (Stream<Path> paths = Files.list(Path.of(uploadDir))) {
                assertEquals(1, paths.count());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void transactionRollbackDeletesStoredPhysicalFile() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        assertThrows(IllegalStateException.class, () ->
                transaction.executeWithoutResult(status -> {
                    User owner = users.findById(ownerId).orElseThrow();
                    storage.store(owner, null, png());
                    throw new IllegalStateException("simulated transaction failure");
                }));

        assertEquals(0, media.count());
        try (Stream<Path> paths = Files.list(Path.of(uploadDir))) {
            assertEquals(0, paths.count());
        }
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "concurrent.png", "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        });
    }

    private void clearUploadDirectory() throws Exception {
        Path root = Path.of(uploadDir);
        Files.createDirectories(root);
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) Files.deleteIfExists(path);
        }
    }
}
