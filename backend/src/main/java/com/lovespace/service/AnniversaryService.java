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
    public AnniversaryService(AnniversaryRepository anniversaries, CurrentUserService current, ViewMapper views) {
        this.anniversaries = anniversaries; this.current = current; this.views = views;
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
        return views.anniversary(anniversaries.save(value));
    }
    @Transactional
    public AnniversaryView update(Authentication auth, Long id, AnniversaryRequest input) {
        User user = current.user(auth); Anniversary value = find(user, id); apply(value, input);
        return views.anniversary(anniversaries.save(value));
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
        value.setTitle(input.title().trim()); value.setEventDate(input.eventDate()); value.setType(input.type().trim());
        value.setRecurringYearly(input.recurringYearly()); value.setReminderDays(input.reminderDays());
        value.setNote(AccountService.trimToNull(input.note()));
    }
}
