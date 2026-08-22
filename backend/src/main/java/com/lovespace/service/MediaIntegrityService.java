package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.MediaIntegrityView;
import com.lovespace.domain.Media;
import com.lovespace.repository.MediaRepository;
import com.lovespace.time.BeijingTime;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MediaIntegrityService {
    private static final Logger log = LoggerFactory.getLogger(MediaIntegrityService.class);
    private static final int MAX_DETAILS = 30;

    private final MediaRepository media;
    private final MediaStorageService storage;
    private final Path quarantineDirectory;
    private final TransactionTemplate transactions;
    private final ReentrantLock scanLock = new ReentrantLock();

    public MediaIntegrityService(MediaRepository media, MediaStorageService storage,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${app.media-quarantine-dir:./data/media-quarantine}") String quarantineDirectory) {
        this.media = media;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
        this.quarantineDirectory = Path.of(quarantineDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initializeDirectory() throws IOException {
        Files.createDirectories(quarantineDirectory);
    }

    public MediaIntegrityView scan() {
        if (!scanLock.tryLock()) {
            throw new com.lovespace.api.error.ApiException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "MEDIA_INTEGRITY_BUSY", "完整性检查正在进行，请稍后重试");
        }
        try {
            return scanLocked();
        } finally {
            scanLock.unlock();
        }
    }

    private MediaIntegrityView scanLocked() {
        // 只把两次短数据库操作放进事务；全盘哈希与文件扫描在事务外执行，避免长时间占用连接
        List<Media> records = transactions.execute(status -> media.findAll(Sort.by(Sort.Direction.ASC, "id")));
        Set<String> referencedNames = records.stream().map(Media::getStoredName).collect(Collectors.toSet());
        Map<Long, String> backfilledHashes = new LinkedHashMap<>();
        List<String> details = new ArrayList<>();
        int healthy = 0;
        int backfilled = 0;
        int missing = 0;
        int sizeMismatches = 0;
        int hashMismatches = 0;

        for (Media value : records) {
            Path path;
            try {
                path = storage.physicalPath(value.getStoredName());
            } catch (RuntimeException ex) {
                missing++;
                addDetail(details, "媒体 " + value.getId() + " 的存储路径无效");
                continue;
            }
            try {
                if (!Files.isRegularFile(path)) {
                    missing++;
                    addDetail(details, "媒体 " + value.getId() + " 文件缺失");
                    continue;
                }
                long actualSize = Files.size(path);
                if (actualSize != value.getByteSize()) {
                    sizeMismatches++;
                    addDetail(details, "媒体 " + value.getId() + " 文件大小不一致");
                    continue;
                }
                String actualHash = MediaStorageService.sha256(path);
                if (value.getSha256() == null || value.getSha256().isBlank()) {
                    backfilledHashes.put(value.getId(), actualHash);
                    backfilled++;
                    healthy++;
                    continue;
                }
                if (!value.getSha256().equalsIgnoreCase(actualHash)) {
                    hashMismatches++;
                    addDetail(details, "媒体 " + value.getId() + " 哈希不一致");
                    continue;
                }
                healthy++;
            } catch (IOException ex) {
                missing++;
                addDetail(details, "媒体 " + value.getId() + " 文件无法读取");
            }
        }

        int orphanFiles = 0;
        int quarantinedFiles = 0;
        int quarantineFailures = 0;
        try {
            Files.createDirectories(storage.storageRoot());
            Files.createDirectories(quarantineDirectory);
            try (var paths = Files.list(storage.storageRoot())) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    if (referencedNames.contains(path.getFileName().toString())) continue;
                    orphanFiles++;
                    try {
                        Files.move(path, quarantineTarget(path), StandardCopyOption.ATOMIC_MOVE);
                        quarantinedFiles++;
                    } catch (AtomicMoveNotSupportedException ex) {
                        try {
                            Files.move(path, quarantineTarget(path));
                            quarantinedFiles++;
                        } catch (IOException moveFailure) {
                            quarantineFailures++;
                            log.warn("Could not quarantine orphan media file {}", path, moveFailure);
                            addDetail(details, "孤儿文件隔离失败：" + path.getFileName());
                        }
                    } catch (IOException ex) {
                        quarantineFailures++;
                        log.warn("Could not quarantine orphan media file {}", path, ex);
                        addDetail(details, "孤儿文件隔离失败：" + path.getFileName());
                    }
                }
            }
        } catch (IOException ex) {
            quarantineFailures++;
            log.warn("Could not scan media directory {}", storage.storageRoot(), ex);
            addDetail(details, "无法扫描上传目录");
        }

        if (!backfilledHashes.isEmpty()) {
            transactions.executeWithoutResult(status ->
                    backfilledHashes.forEach((id, hash) -> media.backfillSha256(id, hash)));
        }

        OffsetDateTime checkedAt = BeijingTime.toOffset(BeijingTime.now());
        return new MediaIntegrityView(records.size(), healthy, backfilled, missing, sizeMismatches,
                hashMismatches, orphanFiles, quarantinedFiles, quarantineFailures, checkedAt, List.copyOf(details));
    }

    private Path quarantineTarget(Path source) {
        String name = source.getFileName().toString();
        return quarantineDirectory.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID() + "-" + name)
                .normalize();
    }

    private void addDetail(List<String> details, String value) {
        if (details.size() < MAX_DETAILS) details.add(value);
    }
}
