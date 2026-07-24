package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.AnniversaryRepository;
import com.lovespace.repository.LetterMessageRepository;
import com.lovespace.repository.NotificationRepository;
import com.lovespace.repository.UserRepository;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final NotificationRepository notifications;
    private final AnniversaryRepository anniversaries;
    private final LetterMessageRepository letterMessages;
    private final UserRepository users;
    private final CurrentUserService current;
    private final ViewMapper views;
    public NotificationService(NotificationRepository notifications, AnniversaryRepository anniversaries,
                               LetterMessageRepository letterMessages, UserRepository users,
                               CurrentUserService current, ViewMapper views) {
        this.notifications = notifications; this.anniversaries = anniversaries;
        this.letterMessages = letterMessages; this.users = users;
        this.current = current; this.views = views;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Authentication auth) {
        User user = current.user(auth);
        List<NotificationView> items = notifications.findTop50ByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(views::notification).toList();
        return new NotificationListResponse(items, notifications.countByUserIdAndReadAtIsNull(user.getId()));
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
        if (value.getReadAt() == null) value.setReadAt(LocalDateTime.now(ZONE));
        return views.notification(notifications.save(value));
    }

    @Transactional
    public void markAllRead(Authentication auth) {
        User user = current.user(auth);
        notifications.markAllReadForUser(user.getId(), LocalDateTime.now(ZONE));
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
        for (Anniversary anniversary : anniversaries.findAll()) {
            long daysUntil = anniversary.daysUntil(today);
            if (daysUntil < 0 || daysUntil > anniversary.getReminderDays()) continue;
            LocalDate occurrence = anniversary.nextOccurrence(today);
            String dedupeKey = REFERENCE_ANNIVERSARY + ":" + anniversary.getId() + ":" + occurrence;
            List<User> members = membersByCouple.computeIfAbsent(anniversary.getCoupleId(), users::findByCoupleIdOrderById);
            for (User member : members) {
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
                .findByScheduledTrueAndNotifiedAtIsNullAndDeliverAtLessThanEqualOrderByDeliverAtAsc(now)) {
            String dedupeKey = "TIME_CAPSULE:" + message.getId();
            if (!notifications.existsByUserIdAndDedupeKey(message.getRecipientId(), dedupeKey)) {
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

    private String reminderBody(long daysUntil, LocalDate occurrence) {
        if (daysUntil == 0) return "今天就是这个重要的日子啦，记得好好庆祝 ❤️";
        return "还有 " + daysUntil + " 天（" + occurrence + "），一起期待吧。";
    }
}
