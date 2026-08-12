package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lovespace.domain.User;
import com.lovespace.security.CurrentUserService;
import com.lovespace.security.SessionPrincipal;
import java.util.List;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    @Test
    void publishesMultipleResourcesOnlyAfterTheTransactionCommits() throws Exception {
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
        sync.connect(authentication, "client_123");
        clearInvocations(emitter);
        TransactionSynchronizationManager.initSynchronization();
        try {
            sync.publishAfterCommit(7L, 99L, "other_client", "POST", List.of("trash", "diaries"));
            verify(emitter, times(0)).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
            TransactionSynchronizationManager.getSynchronizations().forEach(value -> value.afterCommit());
            verify(emitter, times(2)).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void differentTabClientIdsKeepBothSseConnectionsAlive() throws Exception {
        CurrentUserService current = mock(CurrentUserService.class);
        Authentication authentication = mock(Authentication.class);
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        User user = mock(User.class);
        SessionPrincipal principal = new SessionPrincipal(42L, 7L, "alice", "hash", 3);
        when(current.user(authentication)).thenReturn(user);
        when(current.principal(authentication)).thenReturn(principal);
        when(user.getId()).thenReturn(42L);
        when(user.getPasswordVersion()).thenReturn(3);
        Iterator<SseEmitter> emitters = List.of(first, second).iterator();

        RealtimeSyncService sync = new RealtimeSyncService(current, emitters::next);
        sync.connect(authentication, "tab_first_123");
        sync.connect(authentication, "tab_second_123");
        clearInvocations(first, second);

        sync.publish(7L, 42L, "tab_first_123", "PUT", "profile");

        verify(first, never()).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        verify(second).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
    }
}
