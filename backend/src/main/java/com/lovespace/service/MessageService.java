package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.LetterMessageRepository;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final LetterMessageRepository messages;
    private final CurrentUserService current;
    private final ViewMapper views;
    public MessageService(LetterMessageRepository messages, CurrentUserService current, ViewMapper views) {
        this.messages = messages; this.current = current; this.views = views;
    }
    @Transactional(readOnly = true)
    public PageResponse<MessageView> list(Authentication auth, int page, int size) {
        User user = current.user(auth);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deliverAt"));
        Page<LetterMessage> result = messages.findVisibleByCoupleAndUser(
                user.getCouple().getId(), user.getId(), LocalDateTime.now(ZONE), pageable);
        List<MessageView> content = views.messages(result.getContent(), user.getId());
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
    @Transactional
    public MessageView create(Authentication auth, MessageRequest request) {
        User user = current.user(auth); User partner = current.partner(user);
        LocalDateTime now = LocalDateTime.now(ZONE).withNano(0);
        LocalDateTime deliverAt = request.deliverAt();
        boolean scheduled = deliverAt != null;
        if (scheduled && !deliverAt.isAfter(now)) throw ApiException.badRequest("送达时间必须晚于当前时间");
        if (scheduled && deliverAt.isAfter(now.plusYears(10))) throw ApiException.badRequest("送达时间不能超过十年");
        if (!scheduled) deliverAt = now;
        LetterMessage value = new LetterMessage();
        value.setCoupleId(user.getCouple().getId()); value.setAuthorId(user.getId());
        value.setRecipientId(partner.getId()); value.setContent(request.content().trim());
        value.setScheduled(scheduled); value.setDeliverAt(deliverAt);
        return views.message(messages.save(value), user.getId());
    }
    @Transactional
    public MessageView markRead(Authentication auth, Long id) {
        User user = current.user(auth);
        LetterMessage value = messages.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("留言不存在"));
        if (!value.getRecipientId().equals(user.getId())) throw ApiException.forbidden("只有收件人可以标记已读");
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (value.getDeliverAt().isAfter(now)) throw ApiException.notFound("留言不存在");
        if (value.getReadAt() == null) value.setReadAt(now);
        return views.message(messages.save(value));
    }
    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        LetterMessage value = messages.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("留言不存在"));
        if (!value.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能删除自己的留言");
        value.moveToTrash(user.getId(), LocalDateTime.now(ZONE));
        messages.save(value);
    }
}
