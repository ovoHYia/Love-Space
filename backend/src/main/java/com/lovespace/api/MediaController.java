package com.lovespace.api;

import com.lovespace.service.MediaStorageService;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaStorageService storage;
    public MediaController(MediaStorageService storage) { this.storage = storage; }
    @GetMapping("/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> get(Authentication auth,
                                                                  @PathVariable @Positive Long id) {
        MediaStorageService.MediaDownload download = storage.load(auth, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.media().getContentType()))
                .contentLength(download.media().getByteSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.media().getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }
}
