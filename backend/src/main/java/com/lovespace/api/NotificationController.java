package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.NotificationService;
import jakarta.validation.constraints.Positive;
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
    public NotificationListResponse list(Authentication auth) { return notifications.list(auth); }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication auth) { return notifications.unreadCount(auth); }

    @PatchMapping("/{id}/read")
    public NotificationView read(Authentication auth, @PathVariable @Positive Long id) {
        return notifications.markRead(auth, id);
    }

    @PostMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication auth) { notifications.markAllRead(auth); }
}
