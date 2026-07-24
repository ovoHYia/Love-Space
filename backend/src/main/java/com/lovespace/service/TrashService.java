package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.TrashItemView;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrashService {
    private static final String MEMORY = "MEMORY";
    private static final String DIARY = "DIARY";
    private static final String MESSAGE = "MESSAGE";
    private static final String ANNIVERSARY = "ANNIVERSARY";
    private static final String WISH = "WISH";
    private static final String CALENDAR_EVENT = "CALENDAR_EVENT";

    private final MemoryRepository memories;
    private final DiaryRepository diaries;
    private final LetterMessageRepository messages;
    private final AnniversaryRepository anniversaries;
    private final WishRepository wishes;
    private final CalendarEventRepository calendarEvents;
    private final MediaRepository media;
    private final MediaStorageService storage;
    private final CurrentUserService current;

    public TrashService(MemoryRepository memories, DiaryRepository diaries,
                        LetterMessageRepository messages, AnniversaryRepository anniversaries,
                        WishRepository wishes, CalendarEventRepository calendarEvents,
                        MediaRepository media, MediaStorageService storage, CurrentUserService current) {
        this.memories = memories;
        this.diaries = diaries;
        this.messages = messages;
        this.anniversaries = anniversaries;
        this.wishes = wishes;
        this.calendarEvents = calendarEvents;
        this.media = media;
        this.storage = storage;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<TrashItemView> list(Authentication auth) {
        User user = current.user(auth);
        Long coupleId = user.getCouple().getId();
        Long userId = user.getId();
        List<TrashItemView> result = new ArrayList<>();
        memories.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(value -> result.add(view(MEMORY, value, value.getTitle())));
        diaries.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(value -> result.add(view(DIARY, value, value.getTitle())));
        messages.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(value -> result.add(view(MESSAGE, value, "一封信笺")));
        anniversaries.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(value -> result.add(view(ANNIVERSARY, value, value.getTitle())));
        wishes.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(value -> result.add(view(WISH, value, value.getTitle())));
        calendarEvents.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(value -> result.add(view(CALENDAR_EVENT, value, value.getTitle())));
        return result.stream()
                .sorted(Comparator.comparing(TrashItemView::deletedAt).reversed())
                .toList();
    }

    @Transactional
    public void restore(Authentication auth, String rawType, Long id) {
        User user = current.user(auth);
        String type = type(rawType);
        RecoverableContent value = find(user, type, id);
        value.restore();
        save(type, value);
    }

    @Transactional
    public void purge(Authentication auth, String rawType, Long id) {
        User user = current.user(auth);
        String type = type(rawType);
        RecoverableContent value = find(user, type, id);
        purge(type, value);
    }

    @Transactional
    public void empty(Authentication auth) {
        User user = current.user(auth);
        Long coupleId = user.getCouple().getId();
        Long userId = user.getId();
        memories.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId)
                .forEach(value -> purge(MEMORY, value));
        diaries.deleteAll(diaries.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId));
        messages.deleteAll(messages.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId));
        anniversaries.deleteAll(anniversaries.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId));
        wishes.deleteAll(wishes.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId));
        calendarEvents.deleteAll(calendarEvents.findByCoupleIdAndDeletedByOrderByDeletedAtDesc(coupleId, userId));
    }

    private RecoverableContent find(User user, String type, Long id) {
        Long coupleId = user.getCouple().getId();
        Long userId = user.getId();
        return switch (type) {
            case MEMORY -> memories.findByIdAndCoupleIdAndDeletedBy(id, coupleId, userId)
                    .orElseThrow(() -> ApiException.notFound("回收内容不存在"));
            case DIARY -> diaries.findByIdAndCoupleIdAndDeletedBy(id, coupleId, userId)
                    .orElseThrow(() -> ApiException.notFound("回收内容不存在"));
            case MESSAGE -> messages.findByIdAndCoupleIdAndDeletedBy(id, coupleId, userId)
                    .orElseThrow(() -> ApiException.notFound("回收内容不存在"));
            case ANNIVERSARY -> anniversaries.findByIdAndCoupleIdAndDeletedBy(id, coupleId, userId)
                    .orElseThrow(() -> ApiException.notFound("回收内容不存在"));
            case WISH -> wishes.findByIdAndCoupleIdAndDeletedBy(id, coupleId, userId)
                    .orElseThrow(() -> ApiException.notFound("回收内容不存在"));
            case CALENDAR_EVENT -> calendarEvents.findByIdAndCoupleIdAndDeletedBy(id, coupleId, userId)
                    .orElseThrow(() -> ApiException.notFound("回收内容不存在"));
            default -> throw ApiException.badRequest("不支持的回收内容类型");
        };
    }

    private void save(String type, RecoverableContent value) {
        switch (type) {
            case MEMORY -> memories.save((Memory) value);
            case DIARY -> diaries.save((Diary) value);
            case MESSAGE -> messages.save((LetterMessage) value);
            case ANNIVERSARY -> anniversaries.save((Anniversary) value);
            case WISH -> wishes.save((Wish) value);
            case CALENDAR_EVENT -> calendarEvents.save((CalendarEvent) value);
            default -> throw ApiException.badRequest("不支持的回收内容类型");
        }
    }

    private void purge(String type, RecoverableContent value) {
        if (MEMORY.equals(type)) {
            Memory memory = (Memory) value;
            List<Media> attachments = media.findByMemoryId(memory.getId());
            media.deleteAll(attachments);
            memories.delete(memory);
            memories.flush();
            attachments.forEach(storage::deletePhysicalAfterCommit);
            return;
        }
        switch (type) {
            case DIARY -> delete(diaries, (Diary) value);
            case MESSAGE -> delete(messages, (LetterMessage) value);
            case ANNIVERSARY -> delete(anniversaries, (Anniversary) value);
            case WISH -> delete(wishes, (Wish) value);
            case CALENDAR_EVENT -> delete(calendarEvents, (CalendarEvent) value);
            default -> throw ApiException.badRequest("不支持的回收内容类型");
        }
    }

    private <T> void delete(JpaRepository<T, Long> repository, T value) {
        repository.delete(value);
    }

    private TrashItemView view(String type, RecoverableContent value, String title) {
        return new TrashItemView(type, value.getId(), title, value.getDeletedAt());
    }

    private String type(String rawType) {
        if (rawType == null) throw ApiException.badRequest("回收内容类型不能为空");
        String value = rawType.trim().toUpperCase(Locale.ROOT);
        if (!List.of(MEMORY, DIARY, MESSAGE, ANNIVERSARY, WISH, CALENDAR_EVENT).contains(value)) {
            throw ApiException.badRequest("不支持的回收内容类型");
        }
        return value;
    }
}
