package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 500)
    private String body;
    @Column(name = "reference_type", length = 30)
    private String referenceType;
    @Column(name = "reference_id")
    private Long referenceId;
    @Column(name = "dedupe_key", nullable = false, length = 150)
    private String dedupeKey;
    @Column(name = "read_at")
    private LocalDateTime readAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZONE); }
    public Long getId() { return id; }
    public Long getCoupleId() { return coupleId; }
    public void setCoupleId(Long coupleId) { this.coupleId = coupleId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
