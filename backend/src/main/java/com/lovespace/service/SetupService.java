package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SetupService {
    private final CoupleRepository couples;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ViewMapper views;
    public SetupService(CoupleRepository couples, UserRepository users, PasswordEncoder encoder, ViewMapper views) {
        this.couples = couples; this.users = users; this.encoder = encoder; this.views = views;
    }

    @Transactional(readOnly = true)
    public boolean initialized() { return couples.count() > 0; }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public synchronized MeResponse initialize(SetupRequest request) {
        if (couples.count() > 0) throw ApiException.conflict("情侣空间已经初始化");
        String firstName = request.firstUser().username().trim();
        String secondName = request.secondUser().username().trim();
        if (firstName.toLowerCase(Locale.ROOT).equals(secondName.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("两个账号的用户名不能相同");
        }
        validateBcryptLength(request.firstUser().password());
        validateBcryptLength(request.secondUser().password());
        Couple couple = new Couple();
        couple.setSpaceName(request.spaceName().trim());
        couple.setLoveStartedAt(request.loveStartedAt());
        couples.save(couple);
        User first = createUser(couple, firstName, request.firstUser());
        User second = createUser(couple, secondName, request.secondUser());
        return new MeResponse(views.user(first), views.user(second), views.couple(couple));
    }

    private User createUser(Couple couple, String username, InitialUser input) {
        User user = new User();
        user.setCouple(couple);
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(input.password()));
        user.setNickname(input.nickname().trim());
        return users.save(user);
    }

    private void validateBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("密码的 UTF-8 长度不能超过 72 字节");
        }
    }
}
