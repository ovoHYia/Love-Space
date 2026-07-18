package com.lovespace.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record SessionPrincipal(Long userId, Long coupleId, String username, String password, int passwordVersion)
        implements UserDetails {
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
}
