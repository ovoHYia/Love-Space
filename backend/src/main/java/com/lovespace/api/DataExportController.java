package com.lovespace.api;

import com.lovespace.service.DataExportService;
import com.lovespace.service.DataExportService.ExportBundle;
import com.lovespace.api.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/data")
public class DataExportController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final DataExportService exports;
    private final Semaphore exportSlots;

    public DataExportController(DataExportService exports,
                                @Value("${app.data-export.max-concurrent:1}") int maxConcurrent) {
        this.exports = exports;
        if (maxConcurrent <= 0) throw new IllegalArgumentException("app.data-export.max-concurrent must be positive");
        this.exportSlots = new Semaphore(maxConcurrent, true);
    }

    @GetMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> export(Authentication auth) {
        if (!exportSlots.tryAcquire()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_BUSY",
                    "已有数据导出正在进行，请稍后再试");
        }
        ExportBundle bundle;
        try {
            bundle = exports.prepare(auth);
        } catch (RuntimeException ex) {
            exportSlots.release();
            throw ex;
        }
        String filename = "love-space-export-" + LocalDateTime.now(ZONE).format(FILE_TIME) + ".zip";
        StreamingResponseBody body = output -> {
            try {
                exports.writeZip(bundle, output);
            } finally {
                exportSlots.release();
            }
        };
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
