package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "memories")
public class Memory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;
    @Column(name = "author_id", nullable = false)
    private Long authorId;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;
    @Column(length = 200)
    private String location;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(ZONE); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZONE); }
    public Long getId() { return id; }
    public Long getCoupleId() { return coupleId; }
    public void setCoupleId(Long coupleId) { this.coupleId = coupleId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getEventAt() { return eventAt; }
    public void setEventAt(LocalDateTime eventAt) { this.eventAt = eventAt; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Memory that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
