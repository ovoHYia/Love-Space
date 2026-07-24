package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.lovespace.api.error.ApiException;
import com.lovespace.domain.Couple;
import com.lovespace.domain.Media;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.MediaRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.security.CurrentUserService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {
    @TempDir Path uploadDir;
    @Mock MediaRepository media;
    @Mock CoupleRepository couples;
    @Mock UserRepository users;
    @Mock CurrentUserService current;
    @Mock ViewMapper views;

    @Test
    void rejectsInsufficientDiskSpaceWithoutCreatingFile() throws Exception {
        MediaFileSystem diskFull = new MediaFileSystem() {
            @Override long usableSpace(Path directory) { return 4; }
        };
        MediaStorageService storage = storage(diskFull, 1024, 1024, 0);
        prepareQuota(0);

        ApiException error = assertThrows(ApiException.class,
                () -> storage.store(owner(), null, png("disk-full.png")));

        assertEquals(HttpStatus.INSUFFICIENT_STORAGE, error.getStatus());
        assertEquals("INSUFFICIENT_STORAGE", error.getCode());
        assertDirectoryEmpty();
        verify(media, never()).save(any());
    }

    @Test
    void interruptedUploadDeletesPartialFile() throws Exception {
        MediaStorageService storage = storage(new MediaFileSystem(), 1024, 1024, 0);
        prepareQuota(0);

        ApiException error = assertThrows(ApiException.class,
                () -> storage.store(owner(), null, interruptedPng()));

        assertEquals("FILE_STORAGE_ERROR", error.getCode());
        assertDirectoryEmpty();
        verify(media, never()).save(any());
    }

    @Test
    void rejectsSingleFileAndTotalQuotaOveragesWithoutCreatingFile() throws Exception {
        MediaStorageService singleFileStorage = storage(new MediaFileSystem(), 7, 1024, 0);
        ApiException singleFile = assertThrows(ApiException.class,
                () -> singleFileStorage.store(owner(), null, png("too-large.png")));
        assertEquals("FILE_TOO_LARGE", singleFile.getCode());

        MediaStorageService totalQuotaStorage = storage(new MediaFileSystem(), 1024, 15, 0);
        prepareQuota(8);
        ApiException totalQuota = assertThrows(ApiException.class,
                () -> totalQuotaStorage.store(owner(), null, png("quota.png")));
        assertEquals("STORAGE_QUOTA_EXCEEDED", totalQuota.getCode());

        assertDirectoryEmpty();
        verify(media, never()).save(any());
    }

    @Test
    void repositoryFailureDeletesAlreadyCopiedFile() throws Exception {
        MediaStorageService storage = storage(new MediaFileSystem(), 1024, 1024, 0);
        prepareQuota(0);
        when(media.save(any(Media.class))).thenThrow(new DataIntegrityViolationException("simulated"));

        ApiException error = assertThrows(ApiException.class,
                () -> storage.store(owner(), null, png("database-failure.png")));

        assertEquals("FILE_STORAGE_ERROR", error.getCode());
        assertDirectoryEmpty();
    }

    private MediaStorageService storage(MediaFileSystem fileSystem, long maxBytes,
                                        long maxTotalBytes, long minFreeBytes) throws IOException {
        MediaStorageService storage = new MediaStorageService(
                uploadDir.toString(), maxBytes, maxTotalBytes, minFreeBytes,
                media, couples, users, current, views, fileSystem);
        storage.initializeDirectory();
        return storage;
    }

    private void prepareQuota(long usedBytes) {
        Couple couple = owner().getCouple();
        when(couples.findByIdForUpdate(42L)).thenReturn(Optional.of(couple));
        when(media.totalBytesByCoupleId(42L)).thenReturn(usedBytes);
    }

    private User owner() {
        Couple couple = mock(Couple.class);
        lenient().when(couple.getId()).thenReturn(42L);
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(7L);
        lenient().when(owner.getCouple()).thenReturn(couple);
        return owner;
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile(name, name, "image/png", pngBytes());
    }

    private MultipartFile interruptedPng() {
        byte[] bytes = pngBytes();
        AtomicInteger streams = new AtomicInteger();
        return new MockMultipartFile("file", "interrupted.png", "image/png", bytes) {
            @Override
            public InputStream getInputStream() {
                if (streams.getAndIncrement() == 0) return new ByteArrayInputStream(bytes);
                return new InputStream() {
                    private int position;

                    @Override
                    public int read() throws IOException {
                        if (position >= 10) throw new IOException("simulated upload interruption");
                        return bytes[position++] & 0xFF;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        if (position >= 10) throw new IOException("simulated upload interruption");
                        int count = Math.min(length, 10 - position);
                        System.arraycopy(bytes, position, buffer, offset, count);
                        position += count;
                        return count;
                    }
                };
            }
        };
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
    }

    private void assertDirectoryEmpty() throws IOException {
        try (var paths = Files.list(uploadDir)) {
            assertEquals(0, paths.count());
        }
    }
}
