package com.lovespace.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionSystemException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void lockConflictsUseAConflictResponseInsteadOfGenericServerError() {
        ResponseEntity<ApiError> direct = handler.concurrency(
                new ObjectOptimisticLockingFailureException(Object.class, 42L));
        assertEquals(409, direct.getStatusCode().value());
        assertEquals("STALE_UPDATE", direct.getBody().code());

        ResponseEntity<ApiError> wrapped = handler.transaction(
                new TransactionSystemException("commit failed", new OptimisticLockException()));
        assertEquals(409, wrapped.getStatusCode().value());
        assertEquals("STALE_UPDATE", wrapped.getBody().code());
    }

    @Test
    void databaseFailuresAreNotReportedAsStaleData() {
        ResponseEntity<ApiError> transactionFailure = handler.transaction(
                new TransactionSystemException("database unavailable", new IllegalStateException("connection lost")));
        assertEquals(500, transactionFailure.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", transactionFailure.getBody().code());

        ResponseEntity<ApiError> integrityFailure = handler.conflict(
                new DataIntegrityViolationException("database unavailable"));
        assertEquals(500, integrityFailure.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", integrityFailure.getBody().code());
    }
}
