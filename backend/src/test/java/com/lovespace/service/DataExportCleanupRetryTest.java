package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.lovespace.repository.AnniversaryRepository;
import com.lovespace.repository.CalendarEventRepository;
import com.lovespace.repository.DiaryRepository;
import com.lovespace.repository.GameSessionRepository;
import com.lovespace.repository.LetterMessageRepository;
import com.lovespace.repository.MediaRepository;
import com.lovespace.repository.MemoryRepository;
import com.lovespace.repository.MoodRepository;
import com.lovespace.repository.NotificationPreferenceRepository;
import com.lovespace.repository.NotificationRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.repository.WishRepository;
import com.lovespace.security.CurrentUserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class DataExportCleanupRetryTest {
    @TempDir Path exportDir;

    @Test
    void startupScanRemovesExpiredSnapshotsLeftOnDisk() throws Exception {
        Path stale = staleSnapshot();
        DataExportService service = service(false);
        service.startCleanup();
        try {
            assertFalse(Files.exists(stale));
        } finally {
            service.stopCleanup();
        }
    }

    @Test
    void failedDeletionIsRetriedOnTheNextScan() throws Exception {
        Path stale = staleSnapshot();
        DataExportService service = service(true);
        try {
            service.cleanupExpired();
            assertTrue(Files.exists(stale));
            service.cleanupExpired();
            assertFalse(Files.exists(stale));
        } finally {
            service.stopCleanup();
        }
    }

    private Path staleSnapshot() throws Exception {
        Files.createDirectories(exportDir);
        Path stale = exportDir.resolve(".love-space-export-stale.zip");
        Files.write(stale, new byte[]{1});
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minusSeconds(3600)));
        return stale;
    }

    private DataExportService service(boolean failFirst) {
        AtomicBoolean shouldFail = new AtomicBoolean(failFirst);
        return new DataExportService(
                mock(CurrentUserService.class), mock(UserRepository.class), mock(MoodRepository.class),
                mock(MemoryRepository.class), mock(MediaRepository.class), mock(DiaryRepository.class),
                mock(LetterMessageRepository.class), mock(AnniversaryRepository.class), mock(WishRepository.class),
                mock(CalendarEventRepository.class), mock(NotificationRepository.class),
                mock(NotificationPreferenceRepository.class), mock(GameSessionRepository.class),
                mock(MediaStorageService.class), mock(ObjectMapper.class), exportDir.toString(), 10, 0, 2) {
            @Override boolean deleteSnapshotFile(Path path) {
                return shouldFail.compareAndSet(true, false) ? false : super.deleteSnapshotFile(path);
            }
        };
    }
}
