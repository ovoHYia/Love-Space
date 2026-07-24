package com.lovespace.service;

import com.lovespace.api.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Dedicated in-memory limiter for password recovery. All IP-wide requests and
 * username/IP failures are tracked separately so rotating usernames cannot
 * bypass the endpoint-wide guard.
 */
@Service
public class PasswordResetAttemptService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetAttemptService.class);
    private final int maxAttemptsPerIp;
    private final int maxFailuresPerIdentity;
    private final Duration window;
    private final Duration lock;
    private final Duration evictAfter;
    private final ConcurrentHashMap<String, Attempt> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Attempt> identityAttempts = new ConcurrentHashMap<>();

    public PasswordResetAttemptService(
            @Value("${app.security.password-reset.max-attempts-per-ip:20}") int maxAttemptsPerIp,
            @Value("${app.security.password-reset.max-failures-per-identity:5}") int maxFailuresPerIdentity,
            @Value("${app.security.password-reset.window-minutes:15}") long windowMinutes,
            @Value("${app.security.password-reset.lock-minutes:15}") long lockMinutes,
            @Value("${app.security.password-reset.evict-after-minutes:30}") long evictAfterMinutes) {
        this.maxAttemptsPerIp = requirePositive(maxAttemptsPerIp, "max-attempts-per-ip");
        this.maxFailuresPerIdentity = requirePositive(maxFailuresPerIdentity, "max-failures-per-identity");
        this.window = Duration.ofMinutes(requirePositive(windowMinutes, "window-minutes"));
        this.lock = Duration.ofMinutes(requirePositive(lockMinutes, "lock-minutes"));
        this.evictAfter = Duration.ofMinutes(requirePositive(evictAfterMinutes, "evict-after-minutes"));
    }

    public void requireAllowed(String username, String address) {
        Instant now = Instant.now();
        String normalizedAddress = normalizeAddress(address);
        if (isLocked(ipAttempts.get(normalizedAddress), now)
                || isLocked(identityAttempts.get(identityKey(username, normalizedAddress)), now)) {
            log.warn("Password reset temporarily limited from {}", normalizedAddress);
            throw rateLimited();
        }
        if (recordAttempt(ipAttempts, normalizedAddress, maxAttemptsPerIp, now)) {
            log.warn("Password reset request limit reached from {}", normalizedAddress);
            throw rateLimited();
        }
    }

    public void failed(String username, String address) {
        Instant now = Instant.now();
        String normalizedAddress = normalizeAddress(address);
        boolean identityLocked = recordAttempt(
                identityAttempts, identityKey(username, normalizedAddress), maxFailuresPerIdentity, now);
        if (identityLocked) {
            log.warn("Password reset failure limit reached from {}", normalizedAddress);
            throw rateLimited();
        }
    }

    public void succeeded(String username, String address) {
        String normalizedAddress = normalizeAddress(address);
        identityAttempts.remove(identityKey(username, normalizedAddress));
    }

    @Scheduled(fixedRate = 600_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(evictAfter);
        evictExpired(ipAttempts, cutoff);
        evictExpired(identityAttempts, cutoff);
    }

    private boolean recordAttempt(ConcurrentHashMap<String, Attempt> attempts, String key,
                                  int maximum, Instant now) {
        AtomicBoolean locked = new AtomicBoolean();
        attempts.compute(key, (ignored, current) -> {
            if (isLocked(current, now)) {
                locked.set(true);
                return current;
            }
            Attempt next = current == null || !now.isBefore(current.windowStarted().plus(window))
                    ? new Attempt(1, now, Instant.EPOCH, now)
                    : new Attempt(current.failures() + 1, current.windowStarted(), Instant.EPOCH, now);
            if (next.failures() >= maximum) {
                locked.set(true);
                return new Attempt(0, now, now.plus(lock), now);
            }
            return next;
        });
        return locked.get();
    }

    private void evictExpired(ConcurrentHashMap<String, Attempt> attempts, Instant cutoff) {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry ->
                entry.getValue().updatedAt().isBefore(cutoff) && !isLocked(entry.getValue(), now));
    }

    private boolean isLocked(Attempt attempt, Instant now) {
        return attempt != null && now.isBefore(attempt.lockedUntil());
    }

    private String identityKey(String username, String normalizedAddress) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalizedAddress + "|" + normalizedUsername;
    }

    private String normalizeAddress(String address) {
        return address == null || address.isBlank() ? "unknown" : address;
    }

    private ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PASSWORD_RESET_RATE_LIMITED",
                "尝试次数过多，请稍后再试");
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private record Attempt(int failures, Instant windowStarted, Instant lockedUntil, Instant updatedAt) {}
}
