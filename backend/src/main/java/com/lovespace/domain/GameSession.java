package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "game_sessions")
public class GameSession {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String TYPE_TACIT_QUIZ = "TACIT_QUIZ";
    public static final String TYPE_DRAW_GUESS = "DRAW_GUESS";
    public static final String TYPE_MEMORY_GUESS = "MEMORY_GUESS";
    public static final String TYPE_TRUTH_CARD = "TRUTH_CARD";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;
    @Column(name = "game_type", nullable = false, length = 30)
    private String gameType;
    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "current_turn_user_id")
    private Long currentTurnUserId;
    @Column(name = "round_number", nullable = false)
    private int roundNumber = 1;
    @Column(name = "state_json", nullable = false, columnDefinition = "LONGTEXT")
    private String stateJson;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(ZONE); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZONE); }

    public Long getId() { return id; }
    public Long getCoupleId() { return coupleId; }
    public void setCoupleId(Long coupleId) { this.coupleId = coupleId; }
    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getCurrentTurnUserId() { return currentTurnUserId; }
    public void setCurrentTurnUserId(Long currentTurnUserId) { this.currentTurnUserId = currentTurnUserId; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public String getStateJson() { return stateJson; }
    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameSession that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
