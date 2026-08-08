package com.lovespace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lovespace.api.error.ApiException;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {
    @Test
    void rotatingUsernamesCannotBypassIpLimit() {
        LoginAttemptService service = service(3, 8, 100);

        assertDoesNotThrow(() -> service.requireAllowed("alice", "203.0.113.10"));
        service.failed("alice", "203.0.113.10");
        assertDoesNotThrow(() -> service.requireAllowed("bob", "203.0.113.10"));
        service.failed("bob", "203.0.113.10");

        assertThrows(ApiException.class,
                () -> service.requireAllowed("never-seen", "203.0.113.10"));
    }

    @Test
    void identityFailuresAreClearedAfterSuccess() {
        LoginAttemptService service = service(20, 2, 100);

        service.requireAllowed("alice", "203.0.113.11");
        service.failed("alice", "203.0.113.11");
        service.succeeded("alice", "203.0.113.11");

        assertDoesNotThrow(() -> service.failed("alice", "203.0.113.11"));
    }

    @Test
    void cacheCapacityFailsClosedForNewKeys() {
        LoginAttemptService service = service(20, 20, 1);

        assertDoesNotThrow(() -> service.requireAllowed("alice", "203.0.113.12"));
        assertThrows(ApiException.class,
                () -> service.requireAllowed("bob", "203.0.113.13"));
    }

    private LoginAttemptService service(int perIp, int perIdentity, int maxEntries) {
        return new LoginAttemptService(perIp, perIdentity, maxEntries, 15, 15, 30);
    }
}
