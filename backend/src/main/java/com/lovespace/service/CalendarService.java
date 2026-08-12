package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.CalendarEntryView;
import com.lovespace.api.dto.ApiDtos.CalendarEventRequest;
import com.lovespace.api.dto.ApiDtos.CalendarEventUpdateRequest;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import com.lovespace.time.BeijingTime;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {
    private final CalendarEventRepository events;
    private final AnniversaryRepository anniversaries;
    private final MemoryRepository memories;
    private final DiaryRepository diaries;
    private final WishRepository wishes;
    private final LetterMessageRepository messages;
    private final UserRepository users;
    private final CurrentUserService current;
    private final OptimisticUpdateGuard versions;

    public CalendarService(CalendarEventRepository events, AnniversaryRepository anniversaries,
                           MemoryRepository memories, DiaryRepository diaries, WishRepository wishes,
                           LetterMessageRepository messages, UserRepository users,
                           CurrentUserService current, OptimisticUpdateGuard versions) {
        this.events = events;
        this.anniversaries = anniversaries;
        this.memories = memories;
        this.diaries = diaries;
        this.wishes = wishes;
        this.messages = messages;
        this.users = users;
        this.current = current;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryView> list(Authentication auth, LocalDate from, LocalDate to) {
        validateRange(from, to);
        User user = current.user(auth);
        Long coupleId = user.getCouple().getId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Map<Long, String> names = users.findByCoupleIdOrderById(coupleId).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
        List<CalendarEntryView> result = new ArrayList<>();

        events.findActiveInRange(coupleId, start, end).forEach(item -> result.add(entry(
                "CUSTOM", item.getId(), item.getTitle(), item.getDescription(), item.getStartAt(),
                item.getEndAt(), item.isAllDay(), item.getCategory(), item.getLocation(), true,
                item.getCreatedBy(), names, item.getVersion())));
        anniversaries.findByCoupleIdAndDeletedAtIsNullOrderByEventDateAsc(coupleId).forEach(item ->
                occurrences(item, from, to).forEach(date -> result.add(entry(
                        "ANNIVERSARY", item.getId(), item.getTitle(), item.getNote(), date.atStartOfDay(),
                        null, true, item.getType(), null, false, item.getCreatedBy(), names, item.getVersion()))));
        memories.findByCoupleIdAndDeletedAtIsNullAndEventAtGreaterThanEqualAndEventAtLessThanOrderByEventAt(
                coupleId, start, end).forEach(item -> result.add(entry(
                        "MEMORY", item.getId(), item.getTitle(), item.getDescription(), item.getEventAt(),
                        null, !item.isEventTimeKnown(), "MEMORY", item.getLocation(), false, item.getAuthorId(), names,
                        item.getVersion())));
        diaries.findByCoupleIdAndDeletedAtIsNullAndDiaryDateBetweenOrderByDiaryDate(coupleId, from, to)
                .forEach(item -> result.add(entry(
                        "DIARY", item.getId(), item.getTitle(), item.getMood(), item.getDiaryDate().atStartOfDay(),
                        null, true, "DIARY", null, false, item.getAuthorId(), names, item.getVersion())));
        wishes.findByCoupleIdAndDeletedAtIsNullAndTargetDateBetweenOrderByTargetDate(coupleId, from, to)
                .forEach(item -> result.add(entry(
                        "WISH", item.getId(), item.getTitle(), item.getDescription(), item.getTargetDate().atStartOfDay(),
                        null, true, item.getCategory(), null, false, item.getCreatedBy(), names, item.getVersion())));
        messages.findAllVisibleByCoupleAndUser(coupleId, user.getId(), BeijingTime.now()).stream()
                .filter(LetterMessage::isScheduled)
                .filter(item -> !item.getDeliverAt().isBefore(start) && item.getDeliverAt().isBefore(end))
                .forEach(item -> result.add(entry(
                        "LETTER", item.getId(), "定时信笺", null, item.getDeliverAt(), null, false,
                        "LETTER", null, false, item.getAuthorId(), names, item.getVersion())));

        return result.stream()
                .sorted(Comparator.comparing(CalendarEntryView::startAt)
                        .thenComparing(CalendarEntryView::sourceType)
                        .thenComparing(CalendarEntryView::id))
                .toList();
    }

    @Transactional
    public CalendarEntryView create(Authentication auth, CalendarEventRequest input) {
        User user = current.user(auth);
        CalendarEvent value = new CalendarEvent();
        value.setCoupleId(user.getCouple().getId());
        value.setCreatedBy(user.getId());
        apply(value, input);
        CalendarEvent saved = events.saveAndFlush(value);
        return customView(saved, user.getNickname());
    }

    @Transactional
    public CalendarEntryView update(Authentication auth, Long id, CalendarEventUpdateRequest input) {
        User user = current.user(auth);
        CalendarEvent value = find(user, id);
        versions.requireFresh(input.version(), value.getVersion());
        apply(value, input);
        CalendarEvent saved = events.saveAndFlush(value);
        String creator = users.findById(saved.getCreatedBy()).map(User::getNickname).orElse("已注销用户");
        return customView(saved, creator);
    }

    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        CalendarEvent value = find(user, id);
        value.moveToTrash(user.getId(), BeijingTime.now());
        events.save(value);
    }

    private CalendarEvent find(User user, Long id) {
        return events.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("日程不存在"));
    }

    private void apply(CalendarEvent value, CalendarEventRequest input) {
        apply(value, input.title(), input.description(), input.startAt(), input.endAt(),
                input.allDay(), input.category(), input.location());
    }

    private void apply(CalendarEvent value, CalendarEventUpdateRequest input) {
        apply(value, input.title(), input.description(), input.startAt(), input.endAt(),
                input.allDay(), input.category(), input.location());
    }

    private void apply(CalendarEvent value, String title, String description,
                       OffsetDateTime startAt, OffsetDateTime endAt, boolean allDay,
                       String category, String location) {
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw ApiException.badRequest("结束时间必须晚于开始时间");
        }
        value.setTitle(title.trim());
        value.setDescription(AccountService.trimToNull(description));
        value.setStartAt(BeijingTime.toLocal(startAt));
        value.setEndAt(BeijingTime.toLocal(endAt));
        value.setAllDay(allDay);
        value.setCategory(category);
        value.setLocation(AccountService.trimToNull(location));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw ApiException.badRequest("日历日期范围不能为空");
        if (to.isBefore(from)) throw ApiException.badRequest("结束日期不能早于开始日期");
        if (ChronoUnit.DAYS.between(from, to) > 370) throw ApiException.badRequest("单次最多查询 371 天");
    }

    private List<LocalDate> occurrences(Anniversary value, LocalDate from, LocalDate to) {
        if (!value.isRecurringYearly()) {
            return !value.getEventDate().isBefore(from) && !value.getEventDate().isAfter(to)
                    ? List.of(value.getEventDate()) : List.of();
        }
        List<LocalDate> result = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            int month = value.getEventDate().getMonthValue();
            int day = Math.min(value.getEventDate().getDayOfMonth(),
                    Month.of(month).length(Year.isLeap(year)));
            LocalDate occurrence = LocalDate.of(year, month, day);
            if (!occurrence.isBefore(from) && !occurrence.isAfter(to)) result.add(occurrence);
        }
        return result;
    }

    private CalendarEntryView customView(CalendarEvent value, String creator) {
        return new CalendarEntryView("CUSTOM", value.getId(), value.getTitle(), value.getDescription(),
                BeijingTime.toOffset(value.getStartAt()), BeijingTime.toOffset(value.getEndAt()),
                value.isAllDay(), value.getCategory(),
                value.getLocation(), true, value.getCreatedBy(), creator, value.getVersion());
    }

    private CalendarEntryView entry(String sourceType, Long id, String title, String description,
                                    LocalDateTime startAt, LocalDateTime endAt, boolean allDay,
                                    String category, String location, boolean editable,
                                    Long createdBy, Map<Long, String> names, Long version) {
        return new CalendarEntryView(sourceType, id, title, description,
                BeijingTime.toOffset(startAt), BeijingTime.toOffset(endAt), allDay,
                category, location, editable, createdBy,
                names.getOrDefault(createdBy, "已注销用户"), version);
    }
}
