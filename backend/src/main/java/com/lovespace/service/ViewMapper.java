package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.time.BeijingTime;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ViewMapper {
    private final UserRepository users;
    private final MediaRepository media;
    public ViewMapper(UserRepository users, MediaRepository media) { this.users = users; this.media = media; }

    public UserView user(User value) {
        return new UserView(value.getId(), value.getUsername(), value.getNickname(),
                value.getAvatarMediaId() == null ? null : "/api/media/" + value.getAvatarMediaId(),
                value.getRowVersion());
    }
    public CoupleView couple(Couple value) {
        return new CoupleView(value.getId(), value.getSpaceName(), BeijingTime.toOffset(value.getLoveStartedAt()),
                value.getVersion());
    }
    public MoodView mood(Mood value) {
        return new MoodView(value.getId(), value.getUserId(), value.getMoodDate(), value.getEmoji(),
                value.getLabel(), value.getNote(), BeijingTime.toOffset(value.getUpdatedAt()), value.getVersion());
    }
    public MediaView media(Media value) {
        return new MediaView(value.getId(), value.getOriginalName(), value.getContentType(), value.getMediaType(),
                value.getByteSize(), value.getSha256(), "/api/media/" + value.getId(), BeijingTime.toOffset(value.getCreatedAt()));
    }
    public MemoryView memory(Memory value) {
        return memories(List.of(value)).get(0);
    }
    public DiaryView diary(Diary value) {
        return diaries(List.of(value)).get(0);
    }
    public MessageView message(LetterMessage value) { return message(value, null); }
    public MessageView message(LetterMessage value, Long viewerId) {
        return messages(List.of(value), viewerId).get(0);
    }
    public List<MemoryView> memories(List<Memory> values) {
        if (values.isEmpty()) return List.of();
        Map<Long, String> names = userNames(values.get(0).getCoupleId(), values.stream().map(Memory::getAuthorId).collect(java.util.stream.Collectors.toSet()));
        Set<Long> ids = values.stream().map(Memory::getId).collect(java.util.stream.Collectors.toSet());
        Map<Long, List<MediaView>> attachments = media.findByMemoryIdIn(ids).stream().collect(java.util.stream.Collectors.groupingBy(
                Media::getMemoryId, java.util.stream.Collectors.mapping(this::media, java.util.stream.Collectors.toList())));
        return values.stream().map(value -> new MemoryView(value.getId(), value.getAuthorId(),
                names.getOrDefault(value.getAuthorId(), "已注销用户"), value.getTitle(), value.getDescription(),
                BeijingTime.toOffset(value.getEventAt()), value.isEventTimeKnown(), value.getLocation(),
                List.copyOf(value.getTags()), attachments.getOrDefault(value.getId(), List.of()),
                BeijingTime.toOffset(value.getCreatedAt()), BeijingTime.toOffset(value.getUpdatedAt()),
                value.getVersion())).toList();
    }
    public List<DiaryView> diaries(List<Diary> values) {
        if (values.isEmpty()) return List.of();
        Map<Long, String> names = userNames(values.get(0).getCoupleId(), values.stream().map(Diary::getAuthorId).collect(java.util.stream.Collectors.toSet()));
        return values.stream().map(value -> new DiaryView(value.getId(), value.getAuthorId(),
                names.getOrDefault(value.getAuthorId(), "已注销用户"), value.getTitle(), value.getContent(),
                value.getDiaryDate(), value.getMood(), BeijingTime.toOffset(value.getCreatedAt()),
                BeijingTime.toOffset(value.getUpdatedAt()), value.getVersion())).toList();
    }
    public List<MessageView> messages(List<LetterMessage> values, Long viewerId) {
        if (values.isEmpty()) return List.of();
        Set<Long> ids = values.stream().flatMap(value -> java.util.stream.Stream.of(value.getAuthorId(), value.getRecipientId()))
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> names = userNames(values.get(0).getCoupleId(), ids);
        return values.stream().map(value -> {
        boolean sealedForViewer = viewerId != null && viewerId.equals(value.getRecipientId()) && value.getReadAt() == null;
        return new MessageView(value.getId(), value.getAuthorId(), names.get(value.getAuthorId()),
                value.getRecipientId(), names.get(value.getRecipientId()), sealedForViewer ? null : value.getContent(),
                BeijingTime.toOffset(value.getReadAt()), BeijingTime.toOffset(value.getCreatedAt()),
                value.isScheduled(), BeijingTime.toOffset(value.getDeliverAt()), value.getVersion());
        }).toList();
    }
    private Map<Long, String> userNames(Long coupleId, Set<Long> ids) {
        return users.findByIdInAndCoupleId(ids, coupleId).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getNickname));
    }
    public AnniversaryView anniversary(Anniversary value) {
        return new AnniversaryView(value.getId(), value.getCreatedBy(), value.getTitle(), value.getEventDate(),
                value.getType(), value.isRecurringYearly(), value.getReminderDays(), value.getNote(),
                value.daysUntil(BeijingTime.today()), BeijingTime.toOffset(value.getCreatedAt()),
                BeijingTime.toOffset(value.getUpdatedAt()), value.getVersion());
    }
    public WishView wish(Wish value) {
        return wishes(List.of(value)).get(0);
    }
    public List<WishView> wishes(List<Wish> values) {
        if (values.isEmpty()) return List.of();
        Set<Long> ids = values.stream()
                .flatMap(value -> java.util.stream.Stream.of(value.getCreatedBy(), value.getCompletedBy()))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> names = userNames(values.get(0).getCoupleId(), ids);
        return values.stream().map(value -> new WishView(value.getId(), value.getCreatedBy(),
                names.getOrDefault(value.getCreatedBy(), "已注销用户"),
                value.getTitle(), value.getDescription(), value.getCategory(), value.getTargetDate(),
                value.getStatus(), value.getCompletedBy(),
                value.getCompletedBy() == null ? null : names.getOrDefault(value.getCompletedBy(), "已注销用户"),
                BeijingTime.toOffset(value.getCompletedAt()), BeijingTime.toOffset(value.getCreatedAt()),
                BeijingTime.toOffset(value.getUpdatedAt()), value.getVersion())).toList();
    }
    public NotificationView notification(Notification value) {
        return new NotificationView(value.getId(), value.getType(), value.getTitle(), value.getBody(),
                value.getReferenceType(), value.getReferenceId(), BeijingTime.toOffset(value.getReadAt()),
                BeijingTime.toOffset(value.getCreatedAt()));
    }
}
