package com.lovespace.service;

import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DataExportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

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
    private final MediaStorageService storage;
    private final ObjectMapper objectMapper;

    public DataExportService(CurrentUserService current, UserRepository users, MoodRepository moods,
                             MemoryRepository memories, MediaRepository media, DiaryRepository diaries,
                             LetterMessageRepository messages, AnniversaryRepository anniversaries,
                             WishRepository wishes, CalendarEventRepository calendarEvents,
                             NotificationRepository notifications, MediaStorageService storage,
                             ObjectMapper objectMapper) {
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
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public void writeZip(Authentication auth, OutputStream output) throws IOException {
        User user = current.user(auth);
        Long userId = user.getId();
        Long coupleId = user.getCouple().getId();
        LocalDateTime now = LocalDateTime.now(ZONE);

        List<Memory> visibleMemories = visible(memories.findByCoupleIdOrderById(coupleId), userId);
        Set<Long> visibleMemoryIds = visibleMemories.stream().map(Memory::getId).collect(Collectors.toSet());
        List<Media> visibleMedia = media.findByCoupleIdOrderById(coupleId).stream()
                .filter(item -> item.getMemoryId() == null || visibleMemoryIds.contains(item.getMemoryId()))
                .toList();
        Map<Long, Resource> mediaResources = visibleMedia.stream()
                .map(item -> Map.entry(item, storage.loadForExport(item)))
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(entry -> entry.getKey().getId(),
                        entry -> entry.getValue().orElseThrow(), (left, right) -> left, LinkedHashMap::new));

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("formatVersion", 1);
        export.put("generatedAt", now);
        export.put("space", orderedMap(
                "id", coupleId,
                "spaceName", user.getCouple().getSpaceName(),
                "loveStartedAt", user.getCouple().getLoveStartedAt(),
                "createdAt", user.getCouple().getCreatedAt(),
                "updatedAt", user.getCouple().getUpdatedAt()));
        export.put("members", users.findByCoupleIdOrderById(coupleId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "username", item.getUsername(),
                "nickname", item.getNickname(),
                "avatarMediaId", item.getAvatarMediaId(),
                "createdAt", item.getCreatedAt(),
                "updatedAt", item.getUpdatedAt())).toList());
        export.put("moods", moods.findByCoupleIdOrderByMoodDateAscUserIdAsc(coupleId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "userId", item.getUserId(),
                "moodDate", item.getMoodDate(),
                "emoji", item.getEmoji(),
                "label", item.getLabel(),
                "note", item.getNote(),
                "createdAt", item.getCreatedAt(),
                "updatedAt", item.getUpdatedAt())).toList());
        export.put("memories", visibleMemories.stream().map(item -> orderedMap(
                "id", item.getId(),
                "authorId", item.getAuthorId(),
                "title", item.getTitle(),
                "description", item.getDescription(),
                "eventAt", item.getEventAt(),
                "location", item.getLocation(),
                "createdAt", item.getCreatedAt(),
                "updatedAt", item.getUpdatedAt(),
                "deletedAt", item.getDeletedAt())).toList());
        export.put("media", visibleMedia.stream().map(item -> orderedMap(
                "id", item.getId(),
                "ownerId", item.getOwnerId(),
                "memoryId", item.getMemoryId(),
                "originalName", item.getOriginalName(),
                "contentType", item.getContentType(),
                "mediaType", item.getMediaType(),
                "byteSize", item.getByteSize(),
                "archivePath", mediaResources.containsKey(item.getId()) ? archivePath(item) : null,
                "createdAt", item.getCreatedAt())).toList());
        export.put("diaries", visible(diaries.findByCoupleIdOrderById(coupleId), userId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "authorId", item.getAuthorId(),
                "title", item.getTitle(),
                "content", item.getContent(),
                "diaryDate", item.getDiaryDate(),
                "mood", item.getMood(),
                "createdAt", item.getCreatedAt(),
                "updatedAt", item.getUpdatedAt(),
                "deletedAt", item.getDeletedAt())).toList());

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
                "deliverAt", item.getDeliverAt(),
                "readAt", item.getReadAt(),
                "createdAt", item.getCreatedAt(),
                "deletedAt", item.getDeletedAt())).toList());
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
                        "createdAt", item.getCreatedAt(),
                        "updatedAt", item.getUpdatedAt(),
                        "deletedAt", item.getDeletedAt())).toList());
        export.put("wishes", visible(wishes.findByCoupleIdOrderById(coupleId), userId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "createdBy", item.getCreatedBy(),
                "title", item.getTitle(),
                "description", item.getDescription(),
                "category", item.getCategory(),
                "targetDate", item.getTargetDate(),
                "status", item.getStatus(),
                "completedBy", item.getCompletedBy(),
                "completedAt", item.getCompletedAt(),
                "createdAt", item.getCreatedAt(),
                "updatedAt", item.getUpdatedAt(),
                "deletedAt", item.getDeletedAt())).toList());
        export.put("calendarEvents", visible(calendarEvents.findByCoupleIdOrderById(coupleId), userId).stream()
                .map(item -> orderedMap(
                        "id", item.getId(),
                        "createdBy", item.getCreatedBy(),
                        "title", item.getTitle(),
                        "description", item.getDescription(),
                        "startAt", item.getStartAt(),
                        "endAt", item.getEndAt(),
                        "allDay", item.isAllDay(),
                        "category", item.getCategory(),
                        "location", item.getLocation(),
                        "createdAt", item.getCreatedAt(),
                        "updatedAt", item.getUpdatedAt(),
                        "deletedAt", item.getDeletedAt())).toList());
        export.put("notifications", notifications.findByUserIdOrderByCreatedAtAsc(userId).stream().map(item -> orderedMap(
                "id", item.getId(),
                "type", item.getType(),
                "title", item.getTitle(),
                "body", item.getBody(),
                "referenceType", item.getReferenceType(),
                "referenceId", item.getReferenceId(),
                "readAt", item.getReadAt(),
                "createdAt", item.getCreatedAt())).toList());

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("love-space-data.json"));
            zip.write(objectMapper.writeValueAsBytes(export));
            zip.closeEntry();
            for (Media item : visibleMedia) {
                Resource resource = mediaResources.get(item.getId());
                if (resource == null) continue;
                zip.putNextEntry(new ZipEntry(archivePath(item)));
                try (InputStream input = resource.getInputStream()) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
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

    private Map<String, Object> orderedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
