package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.AnniversaryRepository;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnniversaryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final AnniversaryRepository anniversaries;
    private final CurrentUserService current;
    private final ViewMapper views;
    private final OptimisticUpdateGuard versions;
    public AnniversaryService(AnniversaryRepository anniversaries, CurrentUserService current,
                              ViewMapper views, OptimisticUpdateGuard versions) {
        this.anniversaries = anniversaries; this.current = current; this.views = views;
        this.versions = versions;
    }
    @Transactional(readOnly = true)
    public List<AnniversaryView> list(Authentication auth) {
        User user = current.user(auth);
        return anniversaries.findByCoupleIdAndDeletedAtIsNullOrderByEventDateAsc(user.getCouple().getId()).stream()
                .map(views::anniversary)
                .sorted(Comparator.comparingInt((AnniversaryView item) -> item.daysUntil() < 0 ? 1 : 0)
                        .thenComparingLong(item -> item.daysUntil() < 0
                                ? Math.abs(item.daysUntil()) : item.daysUntil()))
                .toList();
    }
    @Transactional
    public AnniversaryView create(Authentication auth, AnniversaryRequest input) {
        User user = current.user(auth); Anniversary value = new Anniversary();
        value.setCoupleId(user.getCouple().getId()); value.setCreatedBy(user.getId()); apply(value, input);
        return views.anniversary(anniversaries.saveAndFlush(value));
    }
    @Transactional
    public AnniversaryView update(Authentication auth, Long id, AnniversaryUpdateRequest input) {
        User user = current.user(auth); Anniversary value = find(user, id);
        versions.requireFresh(input.version(), value.getVersion());
        apply(value, input);
        return views.anniversary(anniversaries.saveAndFlush(value));
    }
    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        Anniversary value = find(user, id);
        value.moveToTrash(user.getId(), LocalDateTime.now(ZONE));
        anniversaries.save(value);
    }
    private Anniversary find(User user, Long id) {
        return anniversaries.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("纪念日不存在"));
    }
    private void apply(Anniversary value, AnniversaryRequest input) {
        apply(value, input.title(), input.eventDate(), input.type(), input.recurringYearly(),
                input.reminderDays(), input.note());
    }
    private void apply(Anniversary value, AnniversaryUpdateRequest input) {
        apply(value, input.title(), input.eventDate(), input.type(), input.recurringYearly(),
                input.reminderDays(), input.note());
    }
    private void apply(Anniversary value, String title, java.time.LocalDate eventDate, String type,
                       boolean recurringYearly, int reminderDays, String note) {
        value.setTitle(title.trim()); value.setEventDate(eventDate); value.setType(type.trim());
        value.setRecurringYearly(recurringYearly); value.setReminderDays(reminderDays);
        value.setNote(AccountService.trimToNull(note));
    }
}
