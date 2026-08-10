package com.lovespace.security;

import com.lovespace.api.error.ApiException;
import com.lovespace.domain.User;
import com.lovespace.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {
    private final UserRepository users;
    public CurrentUserService(UserRepository users) { this.users = users; }

    public SessionPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        return principal;
    }

    @Transactional(readOnly = true)
    public User user(Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        User user = users.findByIdAndCoupleId(principal.userId(), principal.coupleId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "登录状态已失效"));
        return validateSession(principal, user);
    }

    @Transactional
    public User userForUpdate(Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        User user = users.findByIdAndCoupleIdForUpdate(principal.userId(), principal.coupleId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "登录状态已失效"));
        return validateSession(principal, user);
    }

    @Transactional(readOnly = true)
    public User partner(User user) {
        List<User> pair = users.findByCoupleIdOrderById(user.getCouple().getId());
        return pair.stream().filter(candidate -> !candidate.getId().equals(user.getId())).findFirst()
                .orElseThrow(() -> ApiException.conflict("情侣空间缺少伴侣账号"));
    }

    private User validateSession(SessionPrincipal principal, User user) {
        if (user.getPasswordVersion() != principal.passwordVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PASSWORD_CHANGED", "密码已变更，请重新登录");
        }
        return user;
    }
}
