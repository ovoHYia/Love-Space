package com.lovespace.service;

import com.lovespace.domain.User;
import com.lovespace.security.CurrentUserService;
import com.lovespace.security.SessionPrincipal;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RealtimeSyncService {
    private static final long TIMEOUT = 30 * 60 * 1000L;
    private final CurrentUserService current;
    private final Supplier<SseEmitter> emitterFactory;
    private final Map<Long, Map<String, Connection>> clients = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeSyncService(CurrentUserService current) {
        this(current, () -> new SseEmitter(TIMEOUT));
    }

    RealtimeSyncService(CurrentUserService current, Supplier<SseEmitter> emitterFactory) {
        this.current = current;
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter connect(Authentication authentication, String requestedClientId) {
        User user = current.user(authentication);
        SessionPrincipal principal = current.principal(authentication);
        String clientId = normalizeClientId(requestedClientId);
        SseEmitter emitter = emitterFactory.get();
        Connection connection = new Connection(user.getId(), user.getPasswordVersion(), emitter);
        Connection previous = clients.computeIfAbsent(principal.coupleId(), ignored -> new ConcurrentHashMap<>())
                .put(clientId, connection);
        if (previous != null) previous.emitter().complete();
        Runnable cleanup = () -> remove(principal.coupleId(), clientId, connection);
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

    public void disconnectStaleUserConnections(Long userId, int currentPasswordVersion) {
        List<SseEmitter> emitters = new ArrayList<>();
        clients.forEach((coupleId, coupleClients) -> {
            coupleClients.forEach((clientId, connection) -> {
                if (Objects.equals(connection.userId(), userId)
                        && connection.passwordVersion() != currentPasswordVersion
                        && coupleClients.remove(clientId, connection)) {
                    emitters.add(connection.emitter());
                }
            });
            if (coupleClients.isEmpty()) clients.remove(coupleId, coupleClients);
        });
        emitters.forEach(SseEmitter::complete);
    }

    public void publish(Long coupleId, Long actorId, String sourceClientId, String action, String resource) {
        Map<String, Connection> coupleClients = clients.get(coupleId);
        if (coupleClients == null || coupleClients.isEmpty()) return;
        SyncEvent payload = new SyncEvent(action, resource, actorId, normalizeOptionalClientId(sourceClientId), Instant.now());
        coupleClients.forEach((clientId, connection) -> {
            if (clientId.equals(payload.sourceClientId())) return;
            try {
                connection.emitter().send(SseEmitter.event().name("sync").id(UUID.randomUUID().toString())
                        .reconnectTime(2_000).data(payload));
            } catch (IOException | IllegalStateException ex) {
                remove(coupleId, clientId, connection);
            }
        });
    }

    @Scheduled(fixedDelay = 25_000)
    void heartbeat() {
        clients.forEach((coupleId, coupleClients) -> coupleClients.forEach((clientId, connection) -> {
            try {
                connection.emitter().send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException ex) {
                remove(coupleId, clientId, connection);
            }
        }));
    }

    private String normalizeClientId(String value) {
        String normalized = normalizeOptionalClientId(value);
        return normalized == null ? UUID.randomUUID().toString() : normalized;
    }

    private String normalizeOptionalClientId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,80}")) return null;
        return value;
    }

    private void remove(Long coupleId, String clientId, Connection expected) {
        Map<String, Connection> coupleClients = clients.get(coupleId);
        if (coupleClients == null) return;
        coupleClients.remove(clientId, expected);
        if (coupleClients.isEmpty()) clients.remove(coupleId, coupleClients);
    }

    private record Connection(Long userId, int passwordVersion, SseEmitter emitter) {}

    public record SyncEvent(String action, String resource, Long actorId,
                            String sourceClientId, Instant occurredAt) {}
}
