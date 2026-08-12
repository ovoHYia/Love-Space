package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.*;

import com.lovespace.domain.Couple;
import com.lovespace.domain.Media;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.MediaRepository;
import com.lovespace.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class MediaIntegrityIntegrationTest {
    @Autowired MediaStorageService storage;
    @Autowired MediaIntegrityService integrity;
    @Autowired MediaRepository media;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @TempDir static Path uploadDir;
    @TempDir static Path quarantineDir;

    @DynamicPropertySource
    static void registerDirectories(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDir.toString());
        registry.add("app.media-quarantine-dir", () -> quarantineDir.toString());
    }

    private User owner;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) jdbc.execute("TRUNCATE TABLE " + table);
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        clear(uploadDir);
        clear(quarantineDir);
        Couple couple = new Couple();
        couple.setSpaceName("媒体完整性测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.saveAndFlush(couple);
        owner = new User();
        owner.setCouple(couple);
        owner.setUsername("integrity-owner");
        owner.setNickname("完整性检查");
        owner.setPasswordHash(encoder.encode("integrity-owner-pass"));
        owner = users.saveAndFlush(owner);
    }

    @AfterEach
    void cleanup() throws Exception {
        clear(uploadDir);
        clear(quarantineDir);
    }

    @Test
    void identifiesMissingTruncatedHashMismatchAndQuarantinesOrphan() throws Exception {
        Media missing = store("missing.png");
        Media truncated = store("truncated.png");
        Media hashMismatch = store("hash.png");
        Files.delete(uploadDir.resolve(missing.getStoredName()));
        Files.write(uploadDir.resolve(truncated.getStoredName()), new byte[]{1, 2, 3});
        Files.write(uploadDir.resolve(hashMismatch.getStoredName()), pngBytes((byte) 0x22));
        Files.write(uploadDir.resolve("orphan-file.bin"), new byte[]{7, 8, 9});

        var result = integrity.scan();

        assertEquals(3, result.scannedRecords());
        assertEquals(1, result.missingFiles());
        assertEquals(1, result.sizeMismatches());
        assertEquals(1, result.hashMismatches());
        assertEquals(1, result.orphanFiles());
        assertEquals(1, result.quarantinedFiles());
        assertTrue(Files.notExists(uploadDir.resolve("orphan-file.bin")));
        try (Stream<Path> files = Files.list(quarantineDir)) {
            assertEquals(1, files.count());
        }
        assertTrue(media.findById(missing.getId()).isPresent());
        assertTrue(media.findById(truncated.getId()).isPresent());
        assertTrue(media.findById(hashMismatch.getId()).isPresent());
    }

    @Test
    void backfillsHashForExistingHealthyMedia() throws Exception {
        Media value = store("legacy.png");
        value.setSha256(null);
        media.saveAndFlush(value);

        var result = integrity.scan();

        assertEquals(1, result.hashBackfilled());
        assertEquals(1, result.healthyRecords());
        assertNotNull(media.findById(value.getId()).orElseThrow().getSha256());
    }

    @Test
    void orphanLeftAfterDatabaseDeleteIsProcessedByNextScan() throws Exception {
        Media value = store("deleted-record.png");
        media.deleteById(value.getId());
        media.flush();

        var result = integrity.scan();

        assertEquals(1, result.orphanFiles());
        assertEquals(1, result.quarantinedFiles());
    }

    private Media store(String name) {
        return new TransactionTemplate(transactions).execute(status -> storage.store(owner, null,
                new MockMultipartFile("file", name, "image/png", pngBytes((byte) 0x11))));
    }

    private byte[] pngBytes(byte marker) {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                marker, 0x00, 0x00, 0x00};
    }

    private void clear(Path root) throws Exception {
        Files.createDirectories(root);
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
    }
}
