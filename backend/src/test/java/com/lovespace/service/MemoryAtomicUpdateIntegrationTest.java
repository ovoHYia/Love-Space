package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.*;

import com.lovespace.api.dto.ApiDtos.MemoryRequest;
import com.lovespace.api.dto.ApiDtos.MemoryUpdateRequest;
import com.lovespace.api.dto.ApiDtos.MemoryView;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.Couple;
import com.lovespace.domain.Memory;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.MediaRepository;
import com.lovespace.repository.MemoryRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.security.SessionPrincipal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(properties = {
        "app.media-max-bytes=12",
        "app.media-total-max-bytes=4096",
        "app.media-min-free-bytes=0"
})
@ActiveProfiles("test")
class MemoryAtomicUpdateIntegrationTest {
    @Autowired MemoryService memories;
    @Autowired MemoryRepository memoryRepository;
    @Autowired MediaRepository mediaRepository;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @TempDir static Path uploadDir;

    @DynamicPropertySource
    static void registerUploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDir.toString());
    }

    private Long userId;
    private Long coupleId;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        clearUploadDirectory();

        Couple couple = new Couple();
        couple.setSpaceName("回忆原子更新测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        coupleId = couples.saveAndFlush(couple).getId();
        User user = new User();
        user.setCouple(couple);
        user.setUsername("memory-owner");
        user.setNickname("回忆作者");
        user.setPasswordHash(encoder.encode("memory-owner-pass"));
        userId = users.saveAndFlush(user).getId();
    }

    @AfterEach
    void cleanup() throws Exception { clearUploadDirectory(); }

    @Test
    void mediaValidationFailureDoesNotChangeMemoryText() {
        MemoryView original = create("原始文字");
        MemoryUpdateRequest update = update("不应保存的文字", original.version());

        ApiException error = assertThrows(ApiException.class,
                () -> memories.update(authentication(), original.id(), update, List.of(tooLargeFile())));

        assertEquals("FILE_TOO_LARGE", error.getCode());
        Memory persisted = memoryRepository.findById(original.id()).orElseThrow();
        assertEquals("原始文字", persisted.getTitle());
        assertEquals(0, mediaRepository.findByMemoryId(original.id()).size());
    }

    @Test
    void failureDuringSecondFileLeavesNoMediaRowsOrOrphanFiles() throws Exception {
        MemoryView original = create("原始文字");
        MultipartFile second = failWhenStored("second.png");

        assertThrows(ApiException.class, () -> memories.update(authentication(), original.id(),
                update("不应保存的文字", original.version()), List.of(png("first.png"), second)));

        Memory persisted = memoryRepository.findById(original.id()).orElseThrow();
        assertEquals("原始文字", persisted.getTitle());
        assertTrue(mediaRepository.findByMemoryId(original.id()).isEmpty());
        try (Stream<Path> files = Files.list(uploadDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void staleVersionDoesNotWriteTextOrMedia() {
        MemoryView original = create("原始文字");
        MemoryView first = memories.update(authentication(), original.id(),
                update("第一台设备的文字", original.version()), List.of());

        ApiException error = assertThrows(ApiException.class, () -> memories.update(authentication(), original.id(),
                update("旧版本文字", original.version()), List.of(png("stale.png"))));

        assertEquals("STALE_UPDATE", error.getCode());
        Memory persisted = memoryRepository.findById(first.id()).orElseThrow();
        assertEquals("第一台设备的文字", persisted.getTitle());
        assertTrue(mediaRepository.findByMemoryId(first.id()).isEmpty());
    }

    @Test
    void textAndMediaCommitTogether() throws Exception {
        MemoryView original = create("原始文字");

        MemoryView updated = memories.update(authentication(), original.id(),
                update("文字和媒体一起保存", original.version()), List.of(png("together.png")));

        assertEquals("文字和媒体一起保存", updated.title());
        assertEquals(1, mediaRepository.findByMemoryId(original.id()).size());
        try (Stream<Path> files = Files.list(uploadDir)) {
            assertEquals(1, files.count());
        }
    }

    private MemoryView create(String title) {
        return memories.create(authentication(), new MemoryRequest(title, null, eventAt(), true, null, List.of()), List.of());
    }

    private MemoryUpdateRequest update(String title, Long version) {
        return new MemoryUpdateRequest(title, null, eventAt(), true, null, List.of(), version);
    }

    private OffsetDateTime eventAt() {
        return LocalDate.of(2026, 8, 12).atStartOfDay().atOffset(ZoneOffset.ofHours(8));
    }

    private Authentication authentication() {
        User user = users.findById(userId).orElseThrow();
        SessionPrincipal principal = new SessionPrincipal(user.getId(), coupleId, user.getUsername(),
                user.getPasswordHash(), user.getPasswordVersion());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("files", name, "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        });
    }

    private MockMultipartFile tooLargeFile() {
        return new MockMultipartFile("files", "too-large.png", "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00, 0x01
        });
    }

    private MultipartFile failWhenStored(String name) {
        byte[] bytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
        AtomicInteger streams = new AtomicInteger();
        return new MockMultipartFile("files", name, "image/png", bytes) {
            @Override public InputStream getInputStream() throws IOException {
                if (streams.getAndIncrement() == 0) return new ByteArrayInputStream(bytes);
                throw new IOException("simulated second-file failure");
            }
        };
    }

    private void clearUploadDirectory() throws Exception {
        Files.createDirectories(uploadDir);
        try (Stream<Path> files = Files.list(uploadDir)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
    }
}
