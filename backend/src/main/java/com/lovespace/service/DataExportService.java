package com.lovespace.service;

import com.lovespace.domain.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import com.lovespace.time.BeijingTime;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DataExportService {
    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);
    private static final long ZIP_OVERHEAD_BYTES = 1024L * 1024L;

    private final CurrentUserService current;
    private final UserRepository users;
    private final MoodRepository moods;
    private final MemoryRepository memories;
    private final MediaRepository media;
    private final DiaryRepository diaries;
    private final LetterMessageRepository messages;
    private final AnniversaryRepository anniversaries;
    private final WishRepository wishes;
    private final CalendarEventRepository calendarEvents;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository notificationPreferences;
    private final GameSessionRepository gameSessions;
    private final MediaStorageService storage;
    private final ObjectMapper objectMapper;
    private final Path exportDirectory;
    private final long snapshotTtlMinutes;
    private final long minFreeBytes;
    private final int maxPendingSnapshots;
    private final Map<String, ExportSnapshot> prepared = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, String> preparedByUser = new HashMap<>();
    private final java.util.concurrent.ScheduledExecutorService cleanupExecutor =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "love-space-export-cleanup");
                thread.setDaemon(true);
                return thread;
            });

    public DataExportService(CurrentUserService current, UserRepository users, MoodRepository moods,
                             MemoryRepository memories, MediaRepository media, DiaryRepository diaries,
                             LetterMessageRepository messages, AnniversaryRepository anniversaries,
                             WishRepository wishes, CalendarEventRepository calendarEvents,
                             NotificationRepository notifications, NotificationPreferenceRepository notificationPreferences,
                             GameSessionRepository gameSessions,
                             MediaStorageService storage,
                             ObjectMapper objectMapper,
                             @Value("${app.data-export.dir:./data/exports}") String exportDirectory,
                             @Value("${app.data-export.ttl-minutes:10}") long snapshotTtlMinutes,
                             @Value("${app.data-export.min-free-bytes:67108864}") long minFreeBytes,
                             @Value("${app.data-export.max-pending:2}") int maxPendingSnapshots) {
        this.current = current;
        this.users = users;
        this.moods = moods;
        this.memories = memories;
        this.media = media;
        this.diaries = diaries;
        this.messages = messages;
        this.anniversaries = anniversaries;
        this.wishes = wishes;
        this.calendarEvents = calendarEvents;
        this.notifications = notifications;
        this.notificationPreferences = notificationPreferences;
        this.gameSessions = gameSessions;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.exportDirectory = Path.of(exportDirectory).toAbsolutePath().normalize();
        if (snapshotTtlMinutes <= 0) throw new IllegalArgumentException("app.data-export.ttl-minutes must be positive");
        if (minFreeBytes < 0) throw new IllegalArgumentException("app.data-export.min-free-bytes must not be negative");
        if (maxPendingSnapshots <= 0) throw new IllegalArgumentException("app.data-export.max-pending must be positive");
        this.snapshotTtlMinutes = snapshotTtlMinutes;
        this.minFreeBytes = minFreeBytes;
        this.maxPendingSnapshots = maxPendingSnapshots;
    }

    @PostConstruct
    void startCleanup() {
        try {
            Files.createDirectories(exportDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建导出临时目录：" + exportDirectory, ex);
        }
        cleanupExpired();
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpired, 1, 1,
                java.util.concurrent.TimeUnit.MINUTES);
    }

    @PreDestroy
    void stopCleanup() {
        cleanupExecutor.shutdownNow();
        new HashSet<>(prepared.values()).forEach(this::deleteSnapshotFile);
        prepared.clear();
        synchronized (this) { preparedByUser.clear(); }
    }

    @Transactional(readOnly = true)
    public ExportSnapshot prepare(Authentication auth) {
        User user = current.user(auth);
        Long userId = user.getId();
        Long coupleId = user.getCouple().getId();
        LocalDateTime now = BeijingTime.now();

        List<Memory> visibleMemories = visible(memories.findByCoupleIdOrderById(coupleId), userId);
        Set<Long> visibleMemoryIds = visibleMemories.stream().map(Memory::getId).collect(Collectors.toSet());
        List<Media> visibleMedia = media.findByCoupleIdOrderById(coupleId).stream()
                .filter(item -> item.getMemoryId() == null || visibleMemoryIds.contains(item.getMemoryId()))
                .toList();
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("formatVersion", 3);
        export.put("generatedAt", BeijingTime.toOffset(now));
        export.put("space", orderedMap(
                "id", coupleId,
                "spaceName", user.getCouple().getSpaceName(),
                "loveStartedAt", moment(user.getCouple().getLoveStartedAt()),
                "createdAt", moment(user.getCouple().getCreatedAt()),
                "updatedAt", moment(user.getCouple().getUpdatedAt())));
        export.put("members", users.findByCoupleIdOrderById(coupleId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "username", item.getUsername(),
                "nickname", item.getNickname(),
                "avatarMediaId", item.getAvatarMediaId(),
                "createdAt", moment(item.getCreatedAt()),
                "updatedAt", moment(item.getUpdatedAt()))).toList());
        export.put("moods", moods.findByCoupleIdOrderByMoodDateAscUserIdAsc(coupleId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "userId", item.getUserId(),
                "moodDate", item.getMoodDate(),
                "emoji", item.getEmoji(),
                "label", item.getLabel(),
                "note", item.getNote(),
                "createdAt", moment(item.getCreatedAt()),
                "updatedAt", moment(item.getUpdatedAt()))).toList());
        export.put("memories", visibleMemories.stream().map(item -> orderedMap(
                "id", item.getId(),
                "authorId", item.getAuthorId(),
                "title", item.getTitle(),
                "description", item.getDescription(),
                "eventAt", moment(item.getEventAt()),
                "eventTimeKnown", item.isEventTimeKnown(),
                "location", item.getLocation(),
                "tags", List.copyOf(item.getTags()),
                "createdAt", moment(item.getCreatedAt()),
                "updatedAt", moment(item.getUpdatedAt()),
                "deletedAt", moment(item.getDeletedAt()))).toList());
        export.put("media", visibleMedia.stream().map(item -> orderedMap(
                "id", item.getId(),
                "ownerId", item.getOwnerId(),
                "memoryId", item.getMemoryId(),
                "originalName", item.getOriginalName(),
                "contentType", item.getContentType(),
                "mediaType", item.getMediaType(),
                "byteSize", item.getByteSize(),
                "sha256", item.getSha256(),
                "archivePath", archivePath(item),
                "createdAt", moment(item.getCreatedAt()))).toList());
        export.put("diaries", visible(diaries.findByCoupleIdOrderById(coupleId), userId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "authorId", item.getAuthorId(),
                "title", item.getTitle(),
                "content", item.getContent(),
                "diaryDate", item.getDiaryDate(),
                "mood", item.getMood(),
                "createdAt", moment(item.getCreatedAt()),
                "updatedAt", moment(item.getUpdatedAt()),
                "deletedAt", moment(item.getDeletedAt()))).toList());

        LinkedHashMap<Long, LetterMessage> visibleMessages = messages
                .findAllVisibleByCoupleAndUser(coupleId, userId, now).stream()
                .collect(Collectors.toMap(LetterMessage::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        messages.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(item -> visibleMessages.putIfAbsent(item.getId(), item));
        export.put("messages", visibleMessages.values().stream().map(item -> orderedMap(
                "id", item.getId(),
                "authorId", item.getAuthorId(),
                "recipientId", item.getRecipientId(),
                "content", sealedFor(item, userId) ? null : item.getContent(),
                "scheduled", item.isScheduled(),
                "deliverAt", moment(item.getDeliverAt()),
                "readAt", moment(item.getReadAt()),
                "createdAt", moment(item.getCreatedAt()),
                "deletedAt", moment(item.getDeletedAt()))).toList());
        export.put("anniversaries", visible(anniversaries.findByCoupleIdOrderById(coupleId), userId).stream()
                .map(item -> orderedMap(
                        "id", item.getId(),
                        "createdBy", item.getCreatedBy(),
                        "title", item.getTitle(),
                        "eventDate", item.getEventDate(),
                        "type", item.getType(),
                        "recurringYearly", item.isRecurringYearly(),
                        "reminderDays", item.getReminderDays(),
                        "note", item.getNote(),
                        "createdAt", moment(item.getCreatedAt()),
                        "updatedAt", moment(item.getUpdatedAt()),
                        "deletedAt", moment(item.getDeletedAt()))).toList());
        export.put("wishes", visible(wishes.findByCoupleIdOrderById(coupleId), userId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "createdBy", item.getCreatedBy(),
                "title", item.getTitle(),
                "description", item.getDescription(),
                "category", item.getCategory(),
                "targetDate", item.getTargetDate(),
                "status", item.getStatus(),
                "completedBy", item.getCompletedBy(),
                "completedAt", moment(item.getCompletedAt()),
                "createdAt", moment(item.getCreatedAt()),
                "updatedAt", moment(item.getUpdatedAt()),
                "deletedAt", moment(item.getDeletedAt()))).toList());
        export.put("calendarEvents", visible(calendarEvents.findByCoupleIdOrderById(coupleId), userId).stream()
                .map(item -> orderedMap(
                        "id", item.getId(),
                        "createdBy", item.getCreatedBy(),
                        "title", item.getTitle(),
                        "description", item.getDescription(),
                        "startAt", moment(item.getStartAt()),
                        "endAt", moment(item.getEndAt()),
                        "allDay", item.isAllDay(),
                        "category", item.getCategory(),
                        "location", item.getLocation(),
                        "createdAt", moment(item.getCreatedAt()),
                        "updatedAt", moment(item.getUpdatedAt()),
                        "deletedAt", moment(item.getDeletedAt()))).toList());
        export.put("notifications", notifications.findByUserIdOrderByCreatedAtAsc(userId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "type", item.getType(),
                "title", item.getTitle(),
                "body", item.getBody(),
                "referenceType", item.getReferenceType(),
                "referenceId", item.getReferenceId(),
                "readAt", moment(item.getReadAt()),
                "createdAt", moment(item.getCreatedAt()))).toList());
        export.put("notificationPreferences", notificationPreferences.findById(userId)
                .map(item -> orderedMap(
                        "anniversaryEnabled", item.isAnniversaryEnabled(),
                        "letterEnabled", item.isLetterEnabled(),
                        "wishEnabled", item.isWishEnabled(),
                        "updatedAt", moment(item.getUpdatedAt())))
                .orElse(null));
        export.put("games", gameSessions.findByCoupleIdOrderById(coupleId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "gameType", item.getGameType(),
                "status", item.getStatus(),
                "createdBy", item.getCreatedBy(),
                "currentTurnUserId", item.getCurrentTurnUserId(),
                "roundNumber", item.getRoundNumber(),
                "stateJson", GameSession.STATUS_FINISHED.equals(item.getStatus()) ? item.getStateJson() : null,
                "createdAt", moment(item.getCreatedAt()),
                "updatedAt", moment(item.getUpdatedAt()),
                "finishedAt", moment(item.getFinishedAt()))).toList());

        Path snapshot = null;
        try {
            Files.createDirectories(exportDirectory);
            byte[] jsonBytes = objectMapper.writeValueAsBytes(export);
            long mediaBytes = 0;
            for (Media item : visibleMedia) {
                mediaBytes = Math.addExact(mediaBytes, storage.verifiedFileSize(item));
            }
            ensureDiskCapacity(Math.addExact(Math.addExact(jsonBytes.length, mediaBytes), ZIP_OVERHEAD_BYTES));
            snapshot = Files.createTempFile(exportDirectory, ".love-space-export-", ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(snapshot, StandardOpenOption.TRUNCATE_EXISTING))) {
                zip.putNextEntry(new ZipEntry("love-space-data.json"));
                zip.write(jsonBytes);
                zip.closeEntry();
                for (Media item : visibleMedia) {
                    zip.putNextEntry(new ZipEntry(archivePath(item)));
                    try (InputStream input = storage.openForExport(item)) {
                        input.transferTo(zip);
                    } catch (NoSuchFileException | java.io.FileNotFoundException ex) {
                        throw missingMedia(item);
                    } catch (IOException ex) {
                        throw ApiException.conflict("导出读取媒体文件失败，请稍后重试。媒体 ID：" + item.getId());
                    }
                    zip.closeEntry();
                }
                zip.finish();
            }
            String filename = "love-space-export-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
            return new ExportSnapshot(snapshot, userId, coupleId, filename,
                    Instant.now().plusSeconds(snapshotTtlMinutes * 60));
        } catch (ApiException ex) {
            deleteSnapshotFile(snapshot);
            throw ex;
        } catch (IOException ex) {
            deleteSnapshotFile(snapshot);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXPORT_PREPARE_FAILED", "生成数据导出文件失败，请稍后重试");
        }
    }

    private void ensureDiskCapacity(long estimatedBytes) throws IOException {
        long usableBytes = Files.getFileStore(exportDirectory).getUsableSpace();
        if (estimatedBytes > usableBytes || minFreeBytes > usableBytes - estimatedBytes) {
            throw new ApiException(org.springframework.http.HttpStatus.INSUFFICIENT_STORAGE,
                    "INSUFFICIENT_STORAGE", "服务器存储空间不足，无法创建导出文件");
        }
    }

    public synchronized ExportSnapshot register(ExportSnapshot snapshot) {
        String previousToken = preparedByUser.get(snapshot.userId());
        if (previousToken == null && prepared.size() >= maxPendingSnapshots) {
            deleteSnapshotFile(snapshot);
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "EXPORT_PENDING_LIMIT", "待下载导出文件过多，请先完成或等待旧文件清理");
        }
        if (previousToken != null) {
            ExportSnapshot previous = prepared.remove(previousToken);
            preparedByUser.remove(snapshot.userId());
            deleteSnapshotFile(previous);
        }
        String token = UUID.randomUUID().toString();
        prepared.put(token, snapshot);
        preparedByUser.put(snapshot.userId(), token);
        return snapshot.withToken(token);
    }

    @Transactional(readOnly = true)
    public ExportSnapshot claim(Authentication auth, String token) {
        User user = current.user(auth);
        ExportSnapshot snapshot;
        synchronized (this) {
            snapshot = prepared.get(token);
        }
        if (snapshot == null || snapshot.expiresAt().isBefore(Instant.now())) {
            if (snapshot != null) {
                synchronized (this) {
                    if (prepared.remove(token, snapshot)) preparedByUser.remove(snapshot.userId(), token);
                }
                deleteSnapshotFile(snapshot);
            }
            throw ApiException.notFound("导出文件不存在或已过期");
        }
        if (!Objects.equals(snapshot.userId(), user.getId())) {
            throw ApiException.notFound("导出文件不存在或已过期");
        }
        synchronized (this) {
            if (!prepared.remove(token, snapshot)) throw ApiException.notFound("导出文件不存在或已过期");
            preparedByUser.remove(snapshot.userId(), token);
        }
        return snapshot;
    }

    public InputStream openSnapshot(ExportSnapshot snapshot) throws IOException {
        return Files.newInputStream(snapshot.path(), StandardOpenOption.READ);
    }

    public void deleteSnapshot(ExportSnapshot snapshot) {
        deleteSnapshotFile(snapshot);
    }

    public void scheduleCleanup(ExportSnapshot snapshot) {
        cleanupExecutor.schedule(this::cleanupExpired, snapshotTtlMinutes,
                java.util.concurrent.TimeUnit.MINUTES);
    }

    private ApiException missingMedia(Media item) {
        return ApiException.conflict("导出检查发现媒体原文件缺失，请先修复存储完整性。缺失媒体 ID：" + item.getId());
    }

    synchronized void cleanupExpired() {
        Instant now = Instant.now();
        for (var entry : new ArrayList<>(prepared.entrySet())) {
            if (!entry.getValue().expiresAt().isAfter(now)) {
                if (prepared.remove(entry.getKey(), entry.getValue())) {
                    preparedByUser.remove(entry.getValue().userId(), entry.getKey());
                    deleteSnapshotFile(entry.getValue());
                }
            }
        }
        if (!Files.isDirectory(exportDirectory)) return;
        Instant cutoff = now.minusSeconds(snapshotTtlMinutes * 60);
        try (var paths = Files.list(exportDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString().startsWith(".love-space-export-")
                            && value.getFileName().toString().endsWith(".zip"))
                    .toList()) {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) deleteSnapshotFile(path);
                } catch (IOException ex) {
                    log.warn("Could not inspect export snapshot {}. It will be retried.", path, ex);
                }
            }
        } catch (IOException ex) {
            log.warn("Could not scan export directory {}. It will be retried.", exportDirectory, ex);
        }
    }

    void deleteSnapshotFile(ExportSnapshot snapshot) {
        if (snapshot == null) return;
        deleteSnapshotFile(snapshot.path());
    }

    boolean deleteSnapshotFile(Path path) {
        if (path == null) return true;
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException ex) {
            log.warn("Could not delete export snapshot {}. It will be retried.", path, ex);
            return false;
        }
    }

    private boolean sealedFor(LetterMessage item, Long viewerId) {
        return viewerId.equals(item.getRecipientId()) && item.getReadAt() == null;
    }

    private <T extends RecoverableContent> List<T> visible(List<T> values, Long userId) {
        return values.stream()
                .filter(item -> item.getDeletedAt() == null || userId.equals(item.getDeletedBy()))
                .toList();
    }

    private String archivePath(Media item) {
        String name = item.getOriginalName() == null ? "file" : item.getOriginalName();
        String safe = name.replaceAll("[\\r\\n\\\\/:*?\"<>|]", "_").trim();
        if (safe.isBlank()) safe = "file";
        return "media/" + item.getId() + "-" + safe;
    }

    private OffsetDateTime moment(LocalDateTime value) { return BeijingTime.toOffset(value); }

    private Map<String, Object> orderedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    public record ExportSnapshot(Path path, Long userId, Long coupleId, String filename,
                                 Instant expiresAt, String token) {
        public ExportSnapshot(Path path, Long userId, Long coupleId, String filename, Instant expiresAt) {
            this(path, userId, coupleId, filename, expiresAt, null);
        }
        public ExportSnapshot withToken(String value) {
            return new ExportSnapshot(path, userId, coupleId, filename, expiresAt, value);
        }
    }
}
