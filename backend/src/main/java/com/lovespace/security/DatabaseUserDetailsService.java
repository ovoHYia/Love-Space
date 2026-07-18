package com.lovespace.security;

import com.lovespace.domain.User;
import com.lovespace.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public DatabaseUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        return new SessionPrincipal(user.getId(), user.getCouple().getId(), user.getUsername(),
                user.getPasswordHash(), user.getPasswordVersion());
    }
}
