package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "memories")
public class Memory implements RecoverableContent {
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
    @Column(name = "event_time_known", nullable = false)
    private boolean eventTimeKnown = true;
    @Column(length = 200)
    private String location;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "memory_tags", joinColumns = @JoinColumn(name = "memory_id"))
    @Column(name = "tag", nullable = false, length = 30)
    @OrderBy
    private Set<String> tags = new LinkedHashSet<>();
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "deleted_by")
    private Long deletedBy;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
    public boolean isEventTimeKnown() { return eventTimeKnown; }
    public void setEventTimeKnown(boolean eventTimeKnown) { this.eventTimeKnown = eventTimeKnown; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Set<String> getTags() { return tags; }
    public void setTags(Collection<String> tags) {
        this.tags.clear();
        if (tags != null) this.tags.addAll(tags);
    }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Memory that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
