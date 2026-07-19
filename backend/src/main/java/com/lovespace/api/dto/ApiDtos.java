package com.lovespace.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    public record SetupStatus(boolean initialized) {}
    public record CsrfResponse(String token) {}

    public record InitialUser(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 50) String nickname) {}

    public record SetupRequest(
            @NotBlank @Size(max = 100) String spaceName,
            @NotNull @PastOrPresent LocalDateTime loveStartedAt,
            @NotNull @Valid InitialUser firstUser,
            @NotNull @Valid InitialUser secondUser) {}

    public record UserView(Long id, String username, String nickname, String avatarUrl) {}
    public record CoupleView(Long id, String spaceName, LocalDateTime loveStartedAt) {}
    public record MeResponse(UserView user, UserView partner, CoupleView couple) {}

    public record ProfileRequest(@NotBlank @Size(max = 50) String nickname) {}
    public record SpaceNameRequest(@NotBlank @Size(max = 100) String spaceName) {}
    public record PasswordChangeRequest(
            @NotBlank @Size(min = 8, max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record PasswordResetRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(max = 200) String recoveryToken,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record MoodRequest(
            @NotBlank @Size(max = 16) String emoji,
            @NotBlank @Size(max = 30) String label,
            @Size(max = 300) String note) {}
    public record MoodView(Long id, Long userId, LocalDate moodDate, String emoji, String label,
                           String note, LocalDateTime updatedAt) {}

    public record MemoryRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 10000) String description,
            @NotNull LocalDateTime eventAt,
            @Size(max = 200) String location) {}
    public record MediaView(Long id, String originalName, String contentType, String mediaType,
                            long byteSize, String url, LocalDateTime createdAt) {}
    public record MemoryView(Long id, Long authorId, String authorNickname, String title,
                             String description, LocalDateTime eventAt, String location,
                             List<MediaView> media, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean first, boolean last) {}

    public record DiaryRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 50000) String content,
            @NotNull LocalDate diaryDate,
            @Size(max = 30) String mood) {}
    public record DiaryView(Long id, Long authorId, String authorNickname, String title,
                            String content, LocalDate diaryDate, String mood,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record MessageRequest(@NotBlank @Size(max = 10000) String content) {}
    public record MessageView(Long id, Long authorId, String authorNickname,
                              Long recipientId, String recipientNickname, String content,
                              LocalDateTime readAt, LocalDateTime createdAt) {}

    public record AnniversaryRequest(
            @NotBlank @Size(max = 120) String title,
            @NotNull LocalDate eventDate,
            @NotBlank @Size(max = 30) String type,
            boolean recurringYearly,
            @Min(0) @Max(365) int reminderDays,
            @Size(max = 500) String note) {}
    public record AnniversaryView(Long id, Long createdBy, String title, LocalDate eventDate,
                                  String type, boolean recurringYearly, int reminderDays,
                                  String note, long daysUntil, LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {}

    public record DashboardResponse(MeResponse account, List<MoodView> todayMoods,
                                    List<MemoryView> recentMemories, List<DiaryView> recentDiaries,
                                    List<MessageView> recentMessages,
                                    List<AnniversaryView> anniversaries,
                                    List<AnniversaryView> dueReminders,
                                    long unreadMessages) {}
}
