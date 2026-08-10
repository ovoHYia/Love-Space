package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lovespace.domain.User;
import com.lovespace.security.CurrentUserService;
import com.lovespace.security.SessionPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RealtimeSyncServiceTest {
    @Test
    void validatesCurrentUserBeforeRegisteringAndClosesOnlyStaleUserConnections() {
        CurrentUserService current = mock(CurrentUserService.class);
        Authentication authentication = mock(Authentication.class);
        SseEmitter emitter = mock(SseEmitter.class);
        User user = mock(User.class);
        SessionPrincipal principal = new SessionPrincipal(42L, 7L, "alice", "hash", 3);
        when(current.user(authentication)).thenReturn(user);
        when(current.principal(authentication)).thenReturn(principal);
        when(user.getId()).thenReturn(42L);
        when(user.getPasswordVersion()).thenReturn(3);

        RealtimeSyncService sync = new RealtimeSyncService(current, () -> emitter);
        assertSame(emitter, sync.connect(authentication, "client_123"));

        InOrder order = inOrder(current);
        order.verify(current).user(authentication);
        order.verify(current).principal(authentication);
        sync.disconnectStaleUserConnections(41L, 4);
        sync.disconnectStaleUserConnections(42L, 4);
        verify(emitter).complete();
    }
}
