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
    private final LetterMessageRepository messages;
    private final CurrentUserService current;
    private final ViewMapper views;
    public MessageService(LetterMessageRepository messages, CurrentUserService current, ViewMapper views) {
        this.messages = messages; this.current = current; this.views = views;
    }
    @Transactional(readOnly = true)
    public PageResponse<MessageView> list(Authentication auth, int page, int size) {
        User user = current.user(auth);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LetterMessage> result = messages.findByCoupleId(user.getCouple().getId(), pageable);
        List<MessageView> content = views.messages(result.getContent(), user.getId());
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
    @Transactional
    public MessageView create(Authentication auth, MessageRequest request) {
        User user = current.user(auth); User partner = current.partner(user);
        LetterMessage value = new LetterMessage();
        value.setCoupleId(user.getCouple().getId()); value.setAuthorId(user.getId());
        value.setRecipientId(partner.getId()); value.setContent(request.content().trim());
        return views.message(messages.save(value));
    }
    @Transactional
    public MessageView markRead(Authentication auth, Long id) {
        User user = current.user(auth);
        LetterMessage value = messages.findByIdAndCoupleId(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("留言不存在"));
        if (!value.getRecipientId().equals(user.getId())) throw ApiException.forbidden("只有收件人可以标记已读");
        if (value.getReadAt() == null) value.setReadAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        return views.message(messages.save(value));
    }
    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        LetterMessage value = messages.findByIdAndCoupleId(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("留言不存在"));
        if (!value.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能删除自己的留言");
        messages.delete(value);
    }
}
