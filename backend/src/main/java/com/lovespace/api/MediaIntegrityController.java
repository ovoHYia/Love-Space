package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.MediaIntegrityView;
import com.lovespace.service.MediaIntegrityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class MediaIntegrityController {
    private final MediaIntegrityService integrity;
    public MediaIntegrityController(MediaIntegrityService integrity) { this.integrity = integrity; }

    @GetMapping("/media-integrity")
    public MediaIntegrityView scan(Authentication auth) {
        if (auth == null) throw new com.lovespace.api.error.ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        return integrity.scan();
    }
}
