package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.domain.User;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final AccountService accounts;
    private final CurrentUserService current;
    private final MoodRepository moods;
    private final MemoryService memoryService;
    private final DiaryRepository diaries;
    private final LetterMessageRepository messages;
    private final AnniversaryService anniversaryService;
    private final ViewMapper views;
    public DashboardService(AccountService accounts, CurrentUserService current, MoodRepository moods,
                            MemoryService memoryService, DiaryRepository diaries, LetterMessageRepository messages,
                            AnniversaryService anniversaryService, ViewMapper views) {
        this.accounts = accounts; this.current = current; this.moods = moods; this.memoryService = memoryService;
        this.diaries = diaries; this.messages = messages; this.anniversaryService = anniversaryService; this.views = views;
    }
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Authentication auth) {
        User user = current.user(auth); Long coupleId = user.getCouple().getId();
        List<MoodView> todayMoods = moods.findByCoupleIdAndMoodDateOrderByUserId(coupleId, LocalDate.now(ZoneId.of("Asia/Shanghai")))
                .stream().map(views::mood).toList();
        List<MemoryView> recentMemories = memoryService.list(auth, 0, 6, null, null, null).content();
        List<DiaryView> recentDiaries = views.diaries(diaries.findByCoupleIdOrderByDiaryDateDescCreatedAtDesc(coupleId)
                .stream().limit(4).toList());
        List<MessageView> recentMessages = views.messages(messages.findByCoupleIdOrderByCreatedAtDesc(coupleId)
                .stream().limit(4).toList(), user.getId());
        List<AnniversaryView> anniversaries = anniversaryService.list(auth);
        List<AnniversaryView> dueReminders = anniversaries.stream()
                .filter(item -> item.daysUntil() >= 0 && item.daysUntil() <= item.reminderDays()).toList();
        return new DashboardResponse(accounts.me(auth), todayMoods, recentMemories, recentDiaries, recentMessages,
                anniversaries, dueReminders, messages.countByCoupleIdAndRecipientIdAndReadAtIsNull(coupleId, user.getId()));
    }
}
