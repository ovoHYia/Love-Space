package com.lovespace.service;

import com.lovespace.api.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Small in-memory guard for a two-person local application. It deliberately
 * avoids persisting failed passwords while still protecting BCrypt from rapid
 * repeated guesses. Expired entries are evicted periodically.
 */
@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int MAX_FAILURES = 8;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Duration EVICT_AFTER = Duration.ofMinutes(30);
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void requireAllowed(String username, String address) {
        Attempt attempt = attempts.get(key(username, address));
        if (attempt != null && Instant.now().isBefore(attempt.lockedUntil)) {
            log.warn("Login temporarily locked for user {} from {}", username, address);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_TEMPORARILY_LOCKED",
                    "尝试次数过多，请 15 分钟后再试");
        }
    }

    public void failed(String username, String address) {
        String key = key(username, address);
        attempts.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            Attempt next = current == null || now.isAfter(current.windowStarted.plus(WINDOW))
                    ? new Attempt(1, now, Instant.EPOCH)
                    : new Attempt(current.failures + 1, current.windowStarted, current.lockedUntil);
            if (next.failures >= MAX_FAILURES) {
                log.warn("Account locked for user {} from {} after {} failures", username, address, MAX_FAILURES);
                return new Attempt(0, now, now.plus(LOCK));
            }
            return next;
        });
    }

    public void succeeded(String username, String address) { attempts.remove(key(username, address)); }

    @Scheduled(fixedRate = 600_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(EVICT_AFTER);
        attempts.entrySet().removeIf(entry -> {
            Attempt value = entry.getValue();
            return value.windowStarted.isBefore(cutoff) && Instant.now().isAfter(value.lockedUntil);
        });
    }

    private String key(String username, String address) {
        return (address == null ? "" : address) + "|" + username.toLowerCase(Locale.ROOT);
    }

    private record Attempt(int failures, Instant windowStarted, Instant lockedUntil) {}
}
