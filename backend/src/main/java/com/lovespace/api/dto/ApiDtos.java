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
    public record MoodTrendPoint(LocalDate date, Long userId, String nickname, String emoji,
                                 String label, String note, int score) {}
    public record MoodDistributionView(String label, String emoji, long count, int percentage) {}
    public record MoodPersonSummary(Long userId, String nickname, int recordedDays,
                                    double averageScore, String dominantLabel, String dominantEmoji) {}
    public record MonthlyActivitySummary(int memories, int diaries, int letters, int completedWishes) {}
    public record MonthlyHighlight(String type, Long id, String title, LocalDate date) {}
    public record MonthlyReportResponse(
            String month, LocalDate from, LocalDate to, int daysInScope,
            int totalMoodEntries, int recordedDays, int sharedMoodDays, int longestStreak,
            int resonanceRate, int coverageRate, String insight,
            List<MoodTrendPoint> trend, List<MoodDistributionView> distribution,
            List<MoodPersonSummary> people, MonthlyActivitySummary activities,
            List<MonthlyHighlight> highlights) {}

    public record MemoryRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 10000) String description,
            @NotNull LocalDateTime eventAt,
            @Size(max = 200) String location,
            @Size(max = 12) List<@NotBlank @Size(max = 30) String> tags) {}
    public record MediaView(Long id, String originalName, String contentType, String mediaType,
                            long byteSize, String url, LocalDateTime createdAt) {}
    public record MemoryView(Long id, Long authorId, String authorNickname, String title,
                             String description, LocalDateTime eventAt, String location,
                             List<String> tags,
                             List<MediaView> media, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record MemoryTagView(String name, long memoryCount) {}
    public record AlbumItemView(MediaView media, Long memoryId, String memoryTitle,
                                LocalDateTime eventAt, String location, List<String> tags) {}
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

    public record MessageRequest(@NotBlank @Size(max = 10000) String content,
                                 LocalDateTime deliverAt) {}
    public record MessageView(Long id, Long authorId, String authorNickname,
                              Long recipientId, String recipientNickname, String content,
                              LocalDateTime readAt, LocalDateTime createdAt,
                              boolean scheduled, LocalDateTime deliverAt) {}

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

    public record WishRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "TRAVEL|DATE|FOOD|MOVIE|OTHER") String category,
            LocalDate targetDate) {}
    public record WishView(Long id, Long createdBy, String createdByNickname,
                           String title, String description, String category, LocalDate targetDate,
                           String status, Long completedBy, String completedByNickname,
                           LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record CalendarEventRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 1000) String description,
            @NotNull LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            @NotBlank @Pattern(regexp = "DATE|TRAVEL|FAMILY|PERSONAL|OTHER") String category,
            @Size(max = 200) String location) {}
    public record CalendarEntryView(String sourceType, Long id, String title, String description,
                                    LocalDateTime startAt, LocalDateTime endAt, boolean allDay,
                                    String category, String location, boolean editable,
                                    Long createdBy, String createdByNickname) {}

    public record TrashItemView(String type, Long id, String title, LocalDateTime deletedAt) {}

    public record NotificationView(Long id, String type, String title, String body,
                                   String referenceType, Long referenceId, LocalDateTime readAt,
                                   LocalDateTime createdAt) {}
    public record NotificationSummary(long total, long unread, long read,
                                      long anniversaries, long letters, long wishes) {}
    public record NotificationListResponse(
            List<NotificationView> items, int page, int size, long totalElements, int totalPages,
            boolean first, boolean last, long unreadCount, NotificationSummary summary) {}
    public record UnreadCountResponse(long unreadCount) {}
    public record NotificationBatchRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> ids) {}
    public record NotificationBatchResponse(long affected, long unreadCount) {}
    public record NotificationPreferenceRequest(
            @NotNull Boolean anniversaryEnabled,
            @NotNull Boolean letterEnabled,
            @NotNull Boolean wishEnabled) {}
    public record NotificationPreferenceView(
            boolean anniversaryEnabled, boolean letterEnabled, boolean wishEnabled,
            LocalDateTime updatedAt) {}

    public record GameCreateRequest(
            @NotBlank @Pattern(regexp = "TACIT_QUIZ|DRAW_GUESS|MEMORY_GUESS|TRUTH_CARD") String gameType) {}
    public record GameAnswerRequest(@NotBlank @Size(max = 80) String answer) {}
    public record GameGuessRequest(@NotBlank @Size(max = 80) String guess) {}
    public record GamePointRequest(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y) {}
    public record GameStrokeRequest(
            @NotNull @NotBlank @Pattern(regexp = "DRAW|ERASE") String tool,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
            @DecimalMin("1.0") @DecimalMax("32.0") double width,
            @NotEmpty @Size(max = 600) List<@NotNull @Valid GamePointRequest> points) {}
    public record GameStrokeBatchRequest(
            @Positive int roundNumber,
            @NotBlank @Size(max = 80) String operationId,
            @NotEmpty @Size(max = 12) List<@NotNull @Valid GameStrokeRequest> strokes) {}
    public record GameGuessView(Long userId, String nickname, String text,
                                boolean correct, LocalDateTime createdAt) {}
    public record GameMemoryView(String imageUrl, String title, String description,
                                 LocalDateTime eventAt, String location) {}
    public record GameSessionView(
            Long id, String gameType, String status, Long createdBy, String createdByNickname,
            int roundNumber, Long currentTurnUserId, String prompt, List<String> options,
            String myAnswer, String partnerAnswer, boolean answersRevealed, Boolean matched,
            int score, String secretWord, List<GameStrokeRequest> strokes,
            List<GameGuessView> guesses, boolean roundComplete, String cardCategory,
            GameMemoryView memory,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime finishedAt) {}

    public record DashboardResponse(MeResponse account, List<MoodView> todayMoods,
                                    List<MemoryView> recentMemories, List<DiaryView> recentDiaries,
                                    List<MessageView> recentMessages,
                                    List<AnniversaryView> anniversaries,
                                    List<AnniversaryView> dueReminders,
                                    long unreadMessages) {}
}
