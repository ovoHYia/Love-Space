package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;
    @Column(name = "anniversary_enabled", nullable = false)
    private boolean anniversaryEnabled = true;
    @Column(name = "letter_enabled", nullable = false)
    private boolean letterEnabled = true;
    @Column(name = "wish_enabled", nullable = false)
    private boolean wishEnabled = true;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now(ZONE);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCoupleId() { return coupleId; }
    public void setCoupleId(Long coupleId) { this.coupleId = coupleId; }
    public boolean isAnniversaryEnabled() { return anniversaryEnabled; }
    public void setAnniversaryEnabled(boolean anniversaryEnabled) { this.anniversaryEnabled = anniversaryEnabled; }
    public boolean isLetterEnabled() { return letterEnabled; }
    public void setLetterEnabled(boolean letterEnabled) { this.letterEnabled = letterEnabled; }
    public boolean isWishEnabled() { return wishEnabled; }
    public void setWishEnabled(boolean wishEnabled) { this.wishEnabled = wishEnabled; }
    public Long getVersion() { return version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
