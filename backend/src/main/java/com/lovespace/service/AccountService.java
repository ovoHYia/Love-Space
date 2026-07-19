package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final CurrentUserService current;
    private final UserRepository users;
    private final CoupleRepository couples;
    private final MoodRepository moods;
    private final ViewMapper views;
    private final PasswordEncoder encoder;
    public AccountService(CurrentUserService current, UserRepository users, CoupleRepository couples, MoodRepository moods, ViewMapper views,
                          PasswordEncoder encoder) {
        this.current = current; this.users = users; this.couples = couples; this.moods = moods; this.views = views; this.encoder = encoder;
    }
    @Transactional(readOnly = true)
    public MeResponse me(Authentication auth) {
        User user = current.user(auth);
        User partner = current.partner(user);
        return new MeResponse(views.user(user), views.user(partner), views.couple(user.getCouple()));
    }
    @Transactional
    public UserView updateProfile(Authentication auth, ProfileRequest request) {
        User user = current.user(auth);
        user.setNickname(request.nickname().trim());
        return views.user(users.save(user));
    }
    @Transactional
    public CoupleView updateSpaceName(Authentication auth, SpaceNameRequest request) {
        User user = current.user(auth);
        Couple couple = couples.findById(user.getCouple().getId())
                .orElseThrow(() -> ApiException.conflict("情侣空间不存在"));
        couple.setSpaceName(request.spaceName().trim());
        return views.couple(couples.save(couple));
    }
    @Transactional
    public void changePassword(Authentication auth, PasswordChangeRequest request) {
        User user = current.user(auth);
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD", "当前密码不正确");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("新密码的 UTF-8 长度不能超过 72 字节");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("新密码不能与当前密码相同");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setPasswordVersion(user.getPasswordVersion() + 1);
        users.save(user);
        log.info("Password changed for user {}", user.getUsername());
    }
    @Transactional
    public void resetPassword(String username, String newPassword) {
        User user = users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_FAILED", "账号或恢复口令不正确"));
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("新密码的 UTF-8 长度不能超过 72 字节");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setPasswordVersion(user.getPasswordVersion() + 1);
        users.save(user);
        log.info("Password reset for user {}", user.getUsername());
    }
    @Transactional
    public MoodView setTodayMood(Authentication auth, MoodRequest request) {
        User user = current.user(auth);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Mood mood = moods.findByUserIdAndMoodDate(user.getId(), today).orElseGet(Mood::new);
        if (mood.getId() == null) {
            mood.setCoupleId(user.getCouple().getId()); mood.setUserId(user.getId()); mood.setMoodDate(today);
        }
        mood.setEmoji(request.emoji().trim());
        mood.setLabel(request.label().trim());
        mood.setNote(trimToNull(request.note()));
        return views.mood(moods.save(mood));
    }
    static String trimToNull(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
