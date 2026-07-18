package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "media")
public class Media {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "memory_id")
    private Long memoryId;
    @Column(name = "stored_name", nullable = false, unique = true, length = 100)
    private String storedName;
    @Column(name = "original_name", nullable = false)
    private String originalName;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "media_type", nullable = false, length = 16)
    private String mediaType;
    @Column(name = "byte_size", nullable = false)
    private long byteSize;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZONE); }
    public Long getId() { return id; }
    public Long getCoupleId() { return coupleId; }
    public void setCoupleId(Long coupleId) { this.coupleId = coupleId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long byteSize) { this.byteSize = byteSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Media that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
