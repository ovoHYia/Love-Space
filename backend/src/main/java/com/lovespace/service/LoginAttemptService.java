package com.lovespace.service;

import com.lovespace.api.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory login limiter. IP-wide requests and username/IP failures are
 * tracked separately so rotating usernames cannot bypass BCrypt protection.
 */
@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private final int maxAttemptsPerIp;
    private final int maxFailuresPerIdentity;
    private final int maxTrackedEntries;
    private final Duration window;
    private final Duration lock;
    private final Duration evictAfter;
    private final ConcurrentHashMap<String, Attempt> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Attempt> identityAttempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.login.max-attempts-per-ip:30}") int maxAttemptsPerIp,
            @Value("${app.security.login.max-failures-per-identity:8}") int maxFailuresPerIdentity,
            @Value("${app.security.login.max-tracked-entries:5000}") int maxTrackedEntries,
            @Value("${app.security.login.window-minutes:15}") long windowMinutes,
            @Value("${app.security.login.lock-minutes:15}") long lockMinutes,
            @Value("${app.security.login.evict-after-minutes:30}") long evictAfterMinutes) {
        this.maxAttemptsPerIp = requirePositive(maxAttemptsPerIp, "max-attempts-per-ip");
        this.maxFailuresPerIdentity = requirePositive(maxFailuresPerIdentity, "max-failures-per-identity");
        this.maxTrackedEntries = requirePositive(maxTrackedEntries, "max-tracked-entries");
        this.window = Duration.ofMinutes(requirePositive(windowMinutes, "window-minutes"));
        this.lock = Duration.ofMinutes(requirePositive(lockMinutes, "lock-minutes"));
        this.evictAfter = Duration.ofMinutes(requirePositive(evictAfterMinutes, "evict-after-minutes"));
    }

    public void requireAllowed(String username, String address) {
        Instant now = Instant.now();
        String normalizedAddress = normalizeAddress(address);
        if (isLocked(ipAttempts.get(normalizedAddress), now)
                || isLocked(identityAttempts.get(identityKey(username, normalizedAddress)), now)) {
            log.warn("Login temporarily limited from {}", normalizedAddress);
            throw rateLimited();
        }
        if (recordAttempt(ipAttempts, normalizedAddress, maxAttemptsPerIp, now)) {
            log.warn("Login request limit reached from {}", normalizedAddress);
            throw rateLimited();
        }
    }

    public void failed(String username, String address) {
        Instant now = Instant.now();
        String normalizedAddress = normalizeAddress(address);
        if (recordAttempt(identityAttempts, identityKey(username, normalizedAddress),
                maxFailuresPerIdentity, now)) {
            log.warn("Login failure limit reached from {}", normalizedAddress);
            throw rateLimited();
        }
    }

    public void succeeded(String username, String address) {
        String normalizedAddress = normalizeAddress(address);
        ipAttempts.remove(normalizedAddress);
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
        if (!attempts.containsKey(key) && attempts.size() >= maxTrackedEntries) {
            evictExpired();
            if (!attempts.containsKey(key) && attempts.size() >= maxTrackedEntries) return true;
        }
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
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_TEMPORARILY_LOCKED",
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
