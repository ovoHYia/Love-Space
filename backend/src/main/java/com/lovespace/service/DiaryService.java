package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.DiaryRepository;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final DiaryRepository diaries;
    private final CurrentUserService current;
    private final ViewMapper views;
    public DiaryService(DiaryRepository diaries, CurrentUserService current, ViewMapper views) {
        this.diaries = diaries; this.current = current; this.views = views;
    }
    @Transactional(readOnly = true)
    public List<DiaryView> list(Authentication auth, Long authorId) {
        User user = current.user(auth);
        List<Diary> result;
        if (authorId == null) result = diaries.findByCoupleIdAndDeletedAtIsNullOrderByDiaryDateDescCreatedAtDesc(user.getCouple().getId());
        else result = diaries.findByCoupleIdAndAuthorIdAndDeletedAtIsNullOrderByDiaryDateDescCreatedAtDesc(user.getCouple().getId(), authorId);
        return views.diaries(result);
    }
    @Transactional
    public DiaryView create(Authentication auth, DiaryRequest request) {
        User user = current.user(auth);
        Diary value = new Diary(); value.setCoupleId(user.getCouple().getId()); value.setAuthorId(user.getId());
        apply(value, request);
        return views.diary(diaries.save(value));
    }
    @Transactional
    public DiaryView update(Authentication auth, Long id, DiaryRequest request) {
        User user = current.user(auth);
        Diary value = owned(user, id); apply(value, request);
        return views.diary(diaries.save(value));
    }
    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        Diary value = owned(user, id);
        value.moveToTrash(user.getId(), LocalDateTime.now(ZONE));
        diaries.save(value);
    }
    private Diary owned(User user, Long id) {
        Diary value = diaries.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("日记不存在"));
        if (!value.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能修改或删除自己的日记");
        return value;
    }
    private void apply(Diary value, DiaryRequest input) {
        value.setTitle(input.title().trim()); value.setContent(input.content().trim());
        value.setDiaryDate(input.diaryDate()); value.setMood(AccountService.trimToNull(input.mood()));
    }
}
