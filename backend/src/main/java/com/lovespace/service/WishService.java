package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.WishRepository;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final WishRepository wishes;
    private final CurrentUserService current;
    private final NotificationService notifications;
    private final ViewMapper views;
    private final OptimisticUpdateGuard versions;

    public WishService(WishRepository wishes, CurrentUserService current,
                       NotificationService notifications, ViewMapper views,
                       OptimisticUpdateGuard versions) {
        this.wishes = wishes; this.current = current; this.notifications = notifications; this.views = views;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public List<WishView> list(Authentication auth) {
        User user = current.user(auth);
        List<Wish> values = wishes.findByCoupleIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getCouple().getId()).stream()
                .sorted(Comparator.comparingInt((Wish item) -> Wish.STATUS_ACTIVE.equals(item.getStatus()) ? 0 : 1)
                        .thenComparing(Wish::getTargetDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Wish::getCreatedAt, Comparator.reverseOrder()))
                .toList();
        return views.wishes(values);
    }

    @Transactional
    public WishView create(Authentication auth, WishRequest input) {
        User user = current.user(auth);
        Wish value = new Wish();
        value.setCoupleId(user.getCouple().getId());
        value.setCreatedBy(user.getId());
        value.setStatus(Wish.STATUS_ACTIVE);
        apply(value, input);
        Wish saved = wishes.saveAndFlush(value);
        notifications.notifyWishCreated(saved, current.partner(user).getId(), user.getNickname());
        return views.wish(saved);
    }

    @Transactional
    public WishView update(Authentication auth, Long id, WishUpdateRequest input) {
        User user = current.user(auth);
        Wish value = find(user, id);
        versions.requireFresh(input.version(), value.getVersion());
        apply(value, input);
        return views.wish(wishes.saveAndFlush(value));
    }

    @Transactional
    public WishView complete(Authentication auth, Long id) {
        User user = current.user(auth);
        Wish value = find(user, id);
        if (!Wish.STATUS_COMPLETED.equals(value.getStatus())) {
            value.setStatus(Wish.STATUS_COMPLETED);
            value.setCompletedBy(user.getId());
            value.setCompletedAt(LocalDateTime.now(ZONE));
            value = wishes.saveAndFlush(value);
            notifications.notifyWishCompleted(value, current.partner(user).getId(), user.getNickname());
        }
        return views.wish(value);
    }

    @Transactional
    public WishView reopen(Authentication auth, Long id) {
        User user = current.user(auth);
        Wish value = find(user, id);
        value.setStatus(Wish.STATUS_ACTIVE);
        value.setCompletedBy(null);
        value.setCompletedAt(null);
        return views.wish(wishes.saveAndFlush(value));
    }

    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        Wish value = find(user, id);
        value.moveToTrash(user.getId(), LocalDateTime.now(ZONE));
        wishes.save(value);
    }

    private Wish find(User user, Long id) {
        return wishes.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("愿望不存在"));
    }

    private void apply(Wish value, WishRequest input) {
        apply(value, input.title(), input.description(), input.category(), input.targetDate());
    }

    private void apply(Wish value, WishUpdateRequest input) {
        apply(value, input.title(), input.description(), input.category(), input.targetDate());
    }

    private void apply(Wish value, String title, String description, String category, LocalDate targetDate) {
        value.setTitle(title.trim());
        value.setDescription(AccountService.trimToNull(description));
        value.setCategory(category);
        value.setTargetDate(targetDate);
    }
}
