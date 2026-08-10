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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DataExportService {
    private static final long SNAPSHOT_TTL_MINUTES = 10;

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
    private final Map<String, ExportSnapshot> prepared = new java.util.concurrent.ConcurrentHashMap<>();
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
                             @Value("${app.upload-dir:./data/uploads}") String uploadDir) {
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
        this.exportDirectory = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void startCleanup() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpired, 1, 1,
                java.util.concurrent.TimeUnit.MINUTES);
    }

    @PreDestroy
    void stopCleanup() {
        cleanupExecutor.shutdownNow();
        prepared.values().forEach(this::deleteSnapshotFile);
        prepared.clear();
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
            snapshot = Files.createTempFile(exportDirectory, ".love-space-export-", ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(snapshot, StandardOpenOption.TRUNCATE_EXISTING))) {
                zip.putNextEntry(new ZipEntry("love-space-data.json"));
                zip.write(objectMapper.writeValueAsBytes(export));
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
                    Instant.now().plusSeconds(SNAPSHOT_TTL_MINUTES * 60));
        } catch (ApiException ex) {
            deleteSnapshotFile(snapshot);
            throw ex;
        } catch (IOException ex) {
            deleteSnapshotFile(snapshot);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXPORT_PREPARE_FAILED", "生成数据导出文件失败，请稍后重试");
        }
    }

    public ExportSnapshot register(ExportSnapshot snapshot) {
        String token = UUID.randomUUID().toString();
        prepared.put(token, snapshot);
        return snapshot.withToken(token);
    }

    @Transactional(readOnly = true)
    public ExportSnapshot claim(Authentication auth, String token) {
        User user = current.user(auth);
        ExportSnapshot snapshot = prepared.get(token);
        if (snapshot == null || snapshot.expiresAt().isBefore(Instant.now())) {
            if (snapshot != null && prepared.remove(token, snapshot)) deleteSnapshotFile(snapshot);
            throw ApiException.notFound("导出文件不存在或已过期");
        }
        if (!Objects.equals(snapshot.userId(), user.getId())) {
            throw ApiException.notFound("导出文件不存在或已过期");
        }
        if (!prepared.remove(token, snapshot)) throw ApiException.notFound("导出文件不存在或已过期");
        return snapshot;
    }

    public InputStream openSnapshot(ExportSnapshot snapshot) throws IOException {
        return Files.newInputStream(snapshot.path(), StandardOpenOption.READ);
    }

    public void deleteSnapshot(ExportSnapshot snapshot) {
        deleteSnapshotFile(snapshot);
    }

    public void scheduleCleanup(ExportSnapshot snapshot) {
        cleanupExecutor.schedule(() -> deleteSnapshotFile(snapshot), SNAPSHOT_TTL_MINUTES,
                java.util.concurrent.TimeUnit.MINUTES);
    }

    private ApiException missingMedia(Media item) {
        return ApiException.conflict("导出检查发现媒体原文件缺失，请先修复存储完整性。缺失媒体 ID：" + item.getId());
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        prepared.entrySet().removeIf(entry -> {
            if (!entry.getValue().expiresAt().isAfter(now)) {
                deleteSnapshotFile(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private void deleteSnapshotFile(ExportSnapshot snapshot) {
        if (snapshot == null) return;
        deleteSnapshotFile(snapshot.path());
    }

    private void deleteSnapshotFile(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A later cleanup pass can remove a file that was still open on Windows.
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
