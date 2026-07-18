package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "messages")
public class LetterMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;
    @Column(name = "author_id", nullable = false)
    private Long authorId;
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "read_at")
    private LocalDateTime readAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZONE); }
    public Long getId() { return id; }
    public Long getCoupleId() { return coupleId; }
    public void setCoupleId(Long coupleId) { this.coupleId = coupleId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LetterMessage that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
