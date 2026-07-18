package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.service.SetupService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final String setupToken;
    public SetupController(SetupService setup, @Value("${SETUP_TOKEN:}") String setupToken) {
        this.setup = setup;
        this.setupToken = setupToken;
    }
    @GetMapping("/setup/status") public SetupStatus status() { return new SetupStatus(setup.initialized()); }
    @PostMapping("/setup/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse initialize(@RequestHeader(value = "X-Setup-Token", required = false) String suppliedToken,
                                 @Valid @RequestBody SetupRequest request) {
        if (!setup.initialized()) requireSetupToken(suppliedToken);
        MeResponse result = setup.initialize(request);
        log.info("Space initialized: {} — users {} and {}", result.couple().spaceName(),
                result.user().username(), result.partner().username());
        return result;
    }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }

    private void requireSetupToken(String suppliedToken) {
        if (setupToken.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SETUP_TOKEN_REQUIRED",
                    "首次初始化前，请在 .env 设置 SETUP_TOKEN 后重启服务");
        }
        byte[] expected = setupToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = (suppliedToken == null ? "" : suppliedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_SETUP_TOKEN", "初始化口令不正确");
        }
    }
}
