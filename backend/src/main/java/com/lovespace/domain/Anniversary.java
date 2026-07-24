package com.lovespace.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "anniversaries")
public class Anniversary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(name = "recurring_yearly", nullable = false)
    private boolean recurringYearly;
    @Column(name = "reminder_days", nullable = false)
    private int reminderDays;
    @Column(length = 500)
    private String note;
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

    /** The next date this anniversary falls on relative to {@code today}. For recurring
     *  entries this rolls forward to this year (or next), clamping Feb 29 on non-leap years. */
    public LocalDate nextOccurrence(LocalDate today) {
        if (!recurringYearly) return eventDate;
        LocalDate target = onYear(today.getYear());
        return target.isBefore(today) ? onYear(today.getYear() + 1) : target;
    }

    /** Days from {@code today} until the next occurrence (negative if a non-recurring date has passed). */
    public long daysUntil(LocalDate today) {
        return ChronoUnit.DAYS.between(today, nextOccurrence(today));
    }

    private LocalDate onYear(int year) {
        int safeDay = Math.min(eventDate.getDayOfMonth(), Month.of(eventDate.getMonthValue()).length(Year.isLeap(year)));
        return LocalDate.of(year, eventDate.getMonthValue(), safeDay);
    }

    public Long getId() { return id; }
    public Long getCoupleId() { return coupleId; }
    public void setCoupleId(Long coupleId) { this.coupleId = coupleId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isRecurringYearly() { return recurringYearly; }
    public void setRecurringYearly(boolean recurringYearly) { this.recurringYearly = recurringYearly; }
    public int getReminderDays() { return reminderDays; }
    public void setReminderDays(int reminderDays) { this.reminderDays = reminderDays; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Anniversary that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
