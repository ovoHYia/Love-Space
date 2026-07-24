package com.lovespace.api;

import com.lovespace.service.RealtimeSyncService;
import jakarta.validation.constraints.Pattern;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/sync")
public class RealtimeSyncController {
    private final RealtimeSyncService sync;

    public RealtimeSyncController(RealtimeSyncService sync) { this.sync = sync; }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, HttpServletResponse response,
            @RequestParam @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String clientId) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return sync.connect(authentication, clientId);
    }
}
