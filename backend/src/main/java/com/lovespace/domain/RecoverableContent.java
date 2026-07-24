package com.lovespace.domain;

import java.time.LocalDateTime;

public interface RecoverableContent {
    Long getId();
    Long getCoupleId();
    Long getDeletedBy();
    void setDeletedBy(Long deletedBy);
    LocalDateTime getDeletedAt();
    void setDeletedAt(LocalDateTime deletedAt);

    default void moveToTrash(Long userId, LocalDateTime deletedAt) {
        setDeletedBy(userId);
        setDeletedAt(deletedAt);
    }

    default void restore() {
        setDeletedBy(null);
        setDeletedAt(null);
    }
}
