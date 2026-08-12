package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lovespace.api.error.ApiException;
import com.lovespace.domain.Couple;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import com.lovespace.security.SessionPrincipal;

@SpringBootTest(properties = "app.data-export.min-free-bytes=9223372036854775807")
@ActiveProfiles("test")
class DataExportDiskSpaceIntegrationTest {
    @Autowired DataExportService exports;
    @Autowired CoupleRepository couples;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @TempDir static Path exportDir;

    @DynamicPropertySource
    static void registerExportDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.data-export.dir", () -> exportDir.toString());
    }

    private User owner;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[]{"game_sessions", "memory_tags", "notification_preferences", "notifications",
                "calendar_events", "wishes", "anniversaries", "messages", "diaries", "media", "memories",
                "moods", "users", "couples"}) jdbc.execute("TRUNCATE TABLE " + table);
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        Files.createDirectories(exportDir);
        try (Stream<Path> files = Files.list(exportDir)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
        Couple couple = new Couple();
        couple.setSpaceName("导出空间不足测试");
        couple.setLoveStartedAt(LocalDateTime.of(2025, 2, 14, 20, 0));
        couples.saveAndFlush(couple);
        owner = new User();
        owner.setCouple(couple);
        owner.setUsername("export-owner");
        owner.setNickname("导出测试");
        owner.setPasswordHash(encoder.encode("export-owner-pass"));
        owner = users.saveAndFlush(owner);
        User partner = new User();
        partner.setCouple(couple);
        partner.setUsername("export-partner");
        partner.setNickname("伴侣");
        partner.setPasswordHash(encoder.encode("export-partner-pass"));
        users.saveAndFlush(partner);
    }

    @Test
    void insufficientDiskSpaceStopsBeforeZipCreation() throws Exception {
        ApiException error = assertThrows(ApiException.class, () -> exports.prepare(authentication()));

        assertEquals("INSUFFICIENT_STORAGE", error.getCode());
        try (Stream<Path> files = Files.list(exportDir)) {
            assertEquals(0, files.count());
        }
    }

    private Authentication authentication() {
        SessionPrincipal principal = new SessionPrincipal(owner.getId(), owner.getCouple().getId(), owner.getUsername(),
                owner.getPasswordHash(), owner.getPasswordVersion());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
