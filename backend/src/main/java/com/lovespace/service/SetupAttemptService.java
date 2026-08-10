package com.lovespace.service;

import com.lovespace.api.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Bounded in-memory limiter for the one-time initialization endpoint. */
@Service
public class SetupAttemptService {
    private static final Logger log = LoggerFactory.getLogger(SetupAttemptService.class);
    private static final String GLOBAL_KEY = "global";
    private final int maxAttemptsPerIp;
    private final int maxAttemptsGlobal;
    private final int maxTrackedEntries;
    private final Duration window;
    private final Duration lock;
    private final Duration evictAfter;
    private final ConcurrentHashMap<String, Attempt> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Attempt> globalAttempts = new ConcurrentHashMap<>();

    public SetupAttemptService(
            @Value("${app.security.setup.max-attempts-per-ip:10}") int maxAttemptsPerIp,
            @Value("${app.security.setup.max-attempts-global:100}") int maxAttemptsGlobal,
            @Value("${app.security.setup.max-tracked-entries:5000}") int maxTrackedEntries,
            @Value("${app.security.setup.window-minutes:15}") long windowMinutes,
            @Value("${app.security.setup.lock-minutes:15}") long lockMinutes,
            @Value("${app.security.setup.evict-after-minutes:30}") long evictAfterMinutes) {
        this.maxAttemptsPerIp = requirePositive(maxAttemptsPerIp, "max-attempts-per-ip");
        this.maxAttemptsGlobal = requirePositive(maxAttemptsGlobal, "max-attempts-global");
        this.maxTrackedEntries = requirePositive(maxTrackedEntries, "max-tracked-entries");
        this.window = Duration.ofMinutes(requirePositive(windowMinutes, "window-minutes"));
        this.lock = Duration.ofMinutes(requirePositive(lockMinutes, "lock-minutes"));
        this.evictAfter = Duration.ofMinutes(requirePositive(evictAfterMinutes, "evict-after-minutes"));
    }

    public void requireAllowed(String address) {
        Instant now = Instant.now();
        String normalizedAddress = normalizeAddress(address);
        if (isLocked(ipAttempts.get(normalizedAddress), now)
                || isLocked(globalAttempts.get(GLOBAL_KEY), now)) {
            throw rateLimited();
        }
        if (recordAttempt(ipAttempts, normalizedAddress, maxAttemptsPerIp, now)
                || recordAttempt(globalAttempts, GLOBAL_KEY, maxAttemptsGlobal, now)) {
            log.warn("Setup request limit reached from {}", normalizedAddress);
            throw rateLimited();
        }
    }

    public void succeeded(String address) {
        ipAttempts.remove(normalizeAddress(address));
        globalAttempts.remove(GLOBAL_KEY);
    }

    @Scheduled(fixedRate = 600_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(evictAfter);
        evictExpired(ipAttempts, cutoff);
        evictExpired(globalAttempts, cutoff);
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
                    : new Attempt(current.attempts() + 1, current.windowStarted(), Instant.EPOCH, now);
            if (next.attempts() >= maximum) {
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

    private String normalizeAddress(String address) {
        return address == null || address.isBlank() ? "unknown" : address;
    }

    private ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SETUP_RATE_LIMITED",
                "初始化请求过于频繁，请稍后再试");
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private record Attempt(int attempts, Instant windowStarted, Instant lockedUntil, Instant updatedAt) {}
}
