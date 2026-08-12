package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.service.SetupAttemptService;
import com.lovespace.service.SetupService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SetupController {
    private static final Logger log = LoggerFactory.getLogger(SetupController.class);
    private final SetupService setup;
    private final SetupAttemptService attempts;
    private final boolean setupEnabled;
    public SetupController(SetupService setup, SetupAttemptService attempts,
                            @Value("${app.security.setup.enabled:true}") boolean setupEnabled) {
        this.setup = setup; this.attempts = attempts; this.setupEnabled = setupEnabled;
    }
    @GetMapping("/setup/status") public SetupStatus status() {
        return new SetupStatus(setup.initialized());
    }
    @PostMapping("/setup/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse initialize(@RequestHeader(value = "X-Setup-Token", required = false) String suppliedToken,
                                 @Valid @RequestBody SetupRequest request,
                                 HttpServletRequest servletRequest) {
        requireSetupEnabled();
        if (setup.initialized()) {
            throw new ApiException(HttpStatus.CONFLICT, "SETUP_ALREADY_INITIALIZED", "初始化已完成");
        }
        attempts.requireAllowed(servletRequest.getRemoteAddr());
        MeResponse result = setup.initialize(suppliedToken, request);
        attempts.succeeded(servletRequest.getRemoteAddr());
        log.info("Space initialized");
        return result;
    }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }

    private void requireSetupEnabled() {
        if (!setupEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SETUP_DISABLED", "初始化入口已禁用");
        }
    }
}
