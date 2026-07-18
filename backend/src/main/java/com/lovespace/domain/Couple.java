package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "couples")
public class Couple {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "space_name", nullable = false, length = 100)
    private String spaceName;
    @Column(name = "love_started_at", nullable = false)
    private LocalDateTime loveStartedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(ZONE); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZONE); }

    public Long getId() { return id; }
    public String getSpaceName() { return spaceName; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public LocalDateTime getLoveStartedAt() { return loveStartedAt; }
    public void setLoveStartedAt(LocalDateTime loveStartedAt) { this.loveStartedAt = loveStartedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Couple that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
