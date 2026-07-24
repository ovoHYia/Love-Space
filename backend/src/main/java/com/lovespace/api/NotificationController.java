package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public NotificationListResponse list(
            Authentication auth,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|UNREAD|READ") String status,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|ANNIVERSARY|MESSAGE|WISH") String category,
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword) {
        return notifications.list(auth, page, size, status, category, keyword);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication auth) { return notifications.unreadCount(auth); }

    @PatchMapping("/{id}/read")
    public NotificationView read(Authentication auth, @PathVariable @Positive Long id) {
        return notifications.markRead(auth, id);
    }

    @PatchMapping("/{id}/unread")
    public NotificationView unread(Authentication auth, @PathVariable @Positive Long id) {
        return notifications.markUnread(auth, id);
    }

    @PostMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication auth) { notifications.markAllRead(auth); }

    @PostMapping("/batch/read")
    public NotificationBatchResponse readBatch(
            Authentication auth, @Valid @RequestBody NotificationBatchRequest request) {
        return notifications.markBatch(auth, request.ids(), true);
    }

    @PostMapping("/batch/unread")
    public NotificationBatchResponse unreadBatch(
            Authentication auth, @Valid @RequestBody NotificationBatchRequest request) {
        return notifications.markBatch(auth, request.ids(), false);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) {
        notifications.delete(auth, id);
    }

    @DeleteMapping("/batch")
    public NotificationBatchResponse deleteBatch(
            Authentication auth, @Valid @RequestBody NotificationBatchRequest request) {
        return notifications.deleteBatch(auth, request.ids());
    }

    @DeleteMapping("/read")
    public NotificationBatchResponse deleteRead(Authentication auth) {
        return notifications.deleteRead(auth);
    }

    @GetMapping("/preferences")
    public NotificationPreferenceView preferences(Authentication auth) {
        return notifications.preferences(auth);
    }

    @PutMapping("/preferences")
    public NotificationPreferenceView updatePreferences(
            Authentication auth, @Valid @RequestBody NotificationPreferenceRequest request) {
        return notifications.updatePreferences(auth, request);
    }
}
