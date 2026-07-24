package com.lovespace.service;

import com.lovespace.security.SessionPrincipal;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RealtimeSyncService {
    private static final long TIMEOUT = 30 * 60 * 1000L;
    private final Map<Long, Map<String, SseEmitter>> clients = new ConcurrentHashMap<>();

    public SseEmitter connect(Authentication authentication, String requestedClientId) {
        SessionPrincipal principal = principal(authentication);
        String clientId = normalizeClientId(requestedClientId);
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        SseEmitter previous = clients.computeIfAbsent(principal.coupleId(), ignored -> new ConcurrentHashMap<>())
                .put(clientId, emitter);
        if (previous != null) previous.complete();
        Runnable cleanup = () -> remove(principal.coupleId(), clientId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("ready").id(UUID.randomUUID().toString())
                    .data(new SyncEvent("READY", "sync", principal.userId(), null, Instant.now())));
        } catch (IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void publish(Long coupleId, Long actorId, String sourceClientId, String action, String resource) {
        Map<String, SseEmitter> coupleClients = clients.get(coupleId);
        if (coupleClients == null || coupleClients.isEmpty()) return;
        SyncEvent payload = new SyncEvent(action, resource, actorId, normalizeOptionalClientId(sourceClientId), Instant.now());
        coupleClients.forEach((clientId, emitter) -> {
            if (clientId.equals(payload.sourceClientId())) return;
            try {
                emitter.send(SseEmitter.event().name("sync").id(UUID.randomUUID().toString())
                        .reconnectTime(2_000).data(payload));
            } catch (IOException | IllegalStateException ex) {
                remove(coupleId, clientId, emitter);
            }
        });
    }

    @Scheduled(fixedDelay = 25_000)
    void heartbeat() {
        clients.forEach((coupleId, coupleClients) -> coupleClients.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException ex) {
                remove(coupleId, clientId, emitter);
            }
        }));
    }

    private SessionPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new IllegalStateException("实时同步连接缺少登录信息");
        }
        return principal;
    }

    private String normalizeClientId(String value) {
        String normalized = normalizeOptionalClientId(value);
        return normalized == null ? UUID.randomUUID().toString() : normalized;
    }

    private String normalizeOptionalClientId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,80}")) return null;
        return value;
    }

    private void remove(Long coupleId, String clientId, SseEmitter expected) {
        Map<String, SseEmitter> coupleClients = clients.get(coupleId);
        if (coupleClients == null) return;
        coupleClients.remove(clientId, expected);
        if (coupleClients.isEmpty()) clients.remove(coupleId, coupleClients);
    }

    public record SyncEvent(String action, String resource, Long actorId,
                            String sourceClientId, Instant occurredAt) {}
}
