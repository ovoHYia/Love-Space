package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lovespace.api.error.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PasswordResetAttemptServiceTest {
    @Test
    void rejectsNewKeysWhenCapacityIsReached() {
        PasswordResetAttemptService service = service(20, 20, 1);

        assertDoesNotThrow(() -> service.requireAllowed("alice", "203.0.113.20"));
        assertThrows(ApiException.class,
                () -> service.requireAllowed("bob", "203.0.113.21"));
    }

    @Test
    void concurrentNewKeysCannotExceedCapacity() throws Exception {
        PasswordResetAttemptService service = service(20, 20, 1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> (Callable<Boolean>) () -> {
                        try {
                            service.requireAllowed("user-" + index, "198.51.100." + index);
                            return true;
                        } catch (ApiException ex) {
                            return false;
                        }
                    }).toList();
            long admitted = executor.invokeAll(calls).stream()
                    .mapToInt(this::successful)
                    .sum();
            assertEquals(1, admitted);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void expiredEntriesAreEvictedAndDoNotConsumeCapacity() throws Exception {
        PasswordResetAttemptService service = new PasswordResetAttemptService(
                20, 20, 1, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofMillis(1));

        service.requireAllowed("alice", "203.0.113.22");
        Thread.sleep(10);
        service.evictExpired();

        assertDoesNotThrow(() -> service.requireAllowed("bob", "203.0.113.23"));
    }

    private int successful(Future<Boolean> result) {
        try {
            return result.get() ? 1 : 0;
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private PasswordResetAttemptService service(int perIp, int perIdentity, int maxEntries) {
        return new PasswordResetAttemptService(perIp, perIdentity, maxEntries, 15, 15, 30);
    }
}
