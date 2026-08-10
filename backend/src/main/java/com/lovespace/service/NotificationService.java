package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.AnniversaryRepository;
import com.lovespace.repository.LetterMessageRepository;
import com.lovespace.repository.NotificationPreferenceRepository;
import com.lovespace.repository.NotificationRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.security.CurrentUserService;
import com.lovespace.time.BeijingTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    public static final String TYPE_ANNIVERSARY_REMINDER = "ANNIVERSARY_REMINDER";
    public static final String TYPE_TIME_CAPSULE_DELIVERED = "TIME_CAPSULE_DELIVERED";
    public static final String TYPE_WISH_CREATED = "WISH_CREATED";
    public static final String TYPE_WISH_COMPLETED = "WISH_COMPLETED";
    public static final String REFERENCE_ANNIVERSARY = "ANNIVERSARY";
    public static final String REFERENCE_MESSAGE = "MESSAGE";
    public static final String REFERENCE_WISH = "WISH";

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final AnniversaryRepository anniversaries;
    private final LetterMessageRepository letterMessages;
    private final UserRepository users;
    private final CurrentUserService current;
    private final ViewMapper views;
    public NotificationService(NotificationRepository notifications, NotificationPreferenceRepository preferences,
                               AnniversaryRepository anniversaries,
                               LetterMessageRepository letterMessages, UserRepository users,
                               CurrentUserService current, ViewMapper views) {
        this.notifications = notifications; this.preferences = preferences; this.anniversaries = anniversaries;
        this.letterMessages = letterMessages; this.users = users;
        this.current = current; this.views = views;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(
            Authentication auth, int page, int size, String status, String category, String keyword) {
        User user = current.user(auth);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        NotificationSummary summary = summary(user);
        long unread = summary.unread();
        if ((long) page * size > Integer.MAX_VALUE) {
            return pageResponse(page, size, notifications.countSearch(
                    user.getId(), status, category, normalizedKeyword), List.of(), unread, summary);
        }
        Page<Notification> result = notifications.search(
                user.getId(), status, category, normalizedKeyword, PageRequest.of(page, size));
        return pageResponse(page, size, result.getTotalElements(),
                result.getContent().stream().map(views::notification).toList(), unread, summary);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Authentication auth) {
        User user = current.user(auth);
        return new UnreadCountResponse(notifications.countByUserIdAndReadAtIsNull(user.getId()));
    }

    @Transactional
    public NotificationView markRead(Authentication auth, Long id) {
        User user = current.user(auth);
        Notification value = notifications.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("通知不存在"));
        if (value.getReadAt() == null) value.setReadAt(BeijingTime.now());
        return views.notification(notifications.save(value));
    }

    @Transactional
    public NotificationView markUnread(Authentication auth, Long id) {
        User user = current.user(auth);
        Notification value = notifications.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("通知不存在"));
        value.setReadAt(null);
        return views.notification(notifications.save(value));
    }

    @Transactional
    public void markAllRead(Authentication auth) {
        User user = current.user(auth);
        notifications.markAllReadForUser(user.getId(), BeijingTime.now());
    }

    @Transactional
    public NotificationBatchResponse markBatch(Authentication auth, List<Long> ids, boolean read) {
        User user = current.user(auth);
        int affected = read
                ? notifications.markReadForUser(user.getId(), ids, BeijingTime.now())
                : notifications.markUnreadForUser(user.getId(), ids);
        return batchResponse(user.getId(), affected);
    }

    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        Notification value = notifications.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("通知不存在"));
        notifications.delete(value);
    }

    @Transactional
    public NotificationBatchResponse deleteBatch(Authentication auth, List<Long> ids) {
        User user = current.user(auth);
        return batchResponse(user.getId(), notifications.deleteByUserIdAndIdIn(user.getId(), ids));
    }

    @Transactional
    public NotificationBatchResponse deleteRead(Authentication auth) {
        User user = current.user(auth);
        return batchResponse(user.getId(), notifications.deleteByUserIdAndReadAtIsNotNull(user.getId()));
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceView preferences(Authentication auth) {
        User user = current.user(auth);
        return preferences.findById(user.getId())
                .map(this::preferenceView)
                .orElseGet(() -> new NotificationPreferenceView(true, true, true, null));
    }

    @Transactional
    public NotificationPreferenceView updatePreferences(
            Authentication auth, NotificationPreferenceRequest request) {
        User user = current.user(auth);
        NotificationPreference value = findOrCreatePreferences(user);
        value.setAnniversaryEnabled(request.anniversaryEnabled());
        value.setLetterEnabled(request.letterEnabled());
        value.setWishEnabled(request.wishEnabled());
        return preferenceView(preferences.save(value));
    }

    /**
     * Scans every anniversary and, for each one whose next occurrence falls within its reminder
     * window, creates a reminder for both members of the couple. The (user_id, dedupe_key) pair
     * keeps this idempotent, so it can run daily without producing duplicates. Returns the number
     * of notifications created.
     */
    @Transactional
    public int generateAnniversaryReminders(LocalDate today) {
        Map<Long, List<User>> membersByCouple = new HashMap<>();
        int created = 0;
        for (Anniversary anniversary : anniversaries.findByDeletedAtIsNull()) {
            long daysUntil = anniversary.daysUntil(today);
            if (daysUntil < 0 || daysUntil > anniversary.getReminderDays()) continue;
            LocalDate occurrence = anniversary.nextOccurrence(today);
            String dedupeKey = REFERENCE_ANNIVERSARY + ":" + anniversary.getId() + ":" + occurrence;
            List<User> members = membersByCouple.computeIfAbsent(anniversary.getCoupleId(), users::findByCoupleIdOrderById);
            for (User member : members) {
                if (!enabled(member.getId(), REFERENCE_ANNIVERSARY)) continue;
                if (notifications.existsByUserIdAndDedupeKey(member.getId(), dedupeKey)) continue;
                notifications.save(reminder(anniversary, member.getId(), dedupeKey, daysUntil, occurrence));
                created++;
            }
        }
        return created;
    }

    @Transactional
    public int generateScheduledLetterNotifications(LocalDateTime now) {
        int created = 0;
        for (LetterMessage message : letterMessages
                .findByScheduledTrueAndNotifiedAtIsNullAndDeletedAtIsNullAndDeliverAtLessThanEqualOrderByDeliverAtAsc(now)) {
            String dedupeKey = "TIME_CAPSULE:" + message.getId();
            if (enabled(message.getRecipientId(), REFERENCE_MESSAGE)
                    && !notifications.existsByUserIdAndDedupeKey(message.getRecipientId(), dedupeKey)) {
                notifications.save(timeCapsuleNotification(message, dedupeKey));
                created++;
            }
            message.setNotifiedAt(now);
            letterMessages.save(message);
        }
        return created;
    }

    @Transactional
    public void notifyWishCreated(Wish wish, Long recipientId, String actorName) {
        createWishNotification(wish, recipientId, TYPE_WISH_CREATED,
                "新的共同愿望", actorName + " 添加了愿望「" + wish.getTitle() + "」",
                "WISH_CREATED:" + wish.getId());
    }

    @Transactional
    public void notifyWishCompleted(Wish wish, Long recipientId, String actorName) {
        createWishNotification(wish, recipientId, TYPE_WISH_COMPLETED,
                "共同愿望完成啦", actorName + " 完成了愿望「" + wish.getTitle() + "」",
                "WISH_COMPLETED:" + wish.getId() + ":" + wish.getCompletedAt());
    }

    private Notification reminder(Anniversary anniversary, Long userId, String dedupeKey,
                                  long daysUntil, LocalDate occurrence) {
        Notification value = new Notification();
        value.setCoupleId(anniversary.getCoupleId());
        value.setUserId(userId);
        value.setType(TYPE_ANNIVERSARY_REMINDER);
        value.setTitle(anniversary.getTitle());
        value.setBody(reminderBody(daysUntil, occurrence));
        value.setReferenceType(REFERENCE_ANNIVERSARY);
        value.setReferenceId(anniversary.getId());
        value.setDedupeKey(dedupeKey);
        return value;
    }

    private Notification timeCapsuleNotification(LetterMessage message, String dedupeKey) {
        Notification value = new Notification();
        value.setCoupleId(message.getCoupleId());
        value.setUserId(message.getRecipientId());
        value.setType(TYPE_TIME_CAPSULE_DELIVERED);
        value.setTitle("一封时光胶囊到了");
        value.setBody("一封为此刻准备的信已经送达，去亲手拆开吧。");
        value.setReferenceType(REFERENCE_MESSAGE);
        value.setReferenceId(message.getId());
        value.setDedupeKey(dedupeKey);
        return value;
    }

    private void createWishNotification(Wish wish, Long recipientId, String type,
                                        String title, String body, String dedupeKey) {
        if (!enabled(recipientId, REFERENCE_WISH)) return;
        if (notifications.existsByUserIdAndDedupeKey(recipientId, dedupeKey)) return;
        Notification value = new Notification();
        value.setCoupleId(wish.getCoupleId());
        value.setUserId(recipientId);
        value.setType(type);
        value.setTitle(title);
        value.setBody(body);
        value.setReferenceType(REFERENCE_WISH);
        value.setReferenceId(wish.getId());
        value.setDedupeKey(dedupeKey);
        notifications.save(value);
    }

    private NotificationBatchResponse batchResponse(Long userId, long affected) {
        return new NotificationBatchResponse(
                affected, notifications.countByUserIdAndReadAtIsNull(userId));
    }

    private NotificationPreference findOrCreatePreferences(User user) {
        return preferences.findById(user.getId()).orElseGet(() -> {
            NotificationPreference value = new NotificationPreference();
            value.setUserId(user.getId());
            value.setCoupleId(user.getCouple().getId());
            return preferences.save(value);
        });
    }

    private NotificationPreferenceView preferenceView(NotificationPreference value) {
        return new NotificationPreferenceView(
                value.isAnniversaryEnabled(), value.isLetterEnabled(), value.isWishEnabled(),
                BeijingTime.toOffset(value.getUpdatedAt()));
    }

    private NotificationSummary summary(User user) {
        long total = notifications.countByUserId(user.getId());
        long unread = notifications.countByUserIdAndReadAtIsNull(user.getId());
        return new NotificationSummary(
                total, unread, total - unread,
                notifications.countByUserIdAndReferenceType(user.getId(), REFERENCE_ANNIVERSARY),
                notifications.countByUserIdAndReferenceType(user.getId(), REFERENCE_MESSAGE),
                notifications.countByUserIdAndReferenceType(user.getId(), REFERENCE_WISH));
    }

    private NotificationListResponse pageResponse(int page, int size, long total,
                                                  List<NotificationView> items, long unread,
                                                  NotificationSummary summary) {
        long pageCount = total / size + (total % size == 0 ? 0 : 1);
        int totalPages = pageCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pageCount;
        return new NotificationListResponse(items, page, size, total, totalPages,
                page == 0, pageCount == 0 || (long) page >= pageCount - 1, unread, summary);
    }

    private boolean enabled(Long userId, String referenceType) {
        return preferences.findById(userId).map(value -> switch (referenceType) {
            case REFERENCE_ANNIVERSARY -> value.isAnniversaryEnabled();
            case REFERENCE_MESSAGE -> value.isLetterEnabled();
            case REFERENCE_WISH -> value.isWishEnabled();
            default -> true;
        }).orElse(true);
    }

    private String reminderBody(long daysUntil, LocalDate occurrence) {
        if (daysUntil == 0) return "今天就是这个重要的日子啦，记得好好庆祝 ❤️";
        return "还有 " + daysUntil + " 天（" + occurrence + "），一起期待吧。";
    }
}
