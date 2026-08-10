package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.ExportPreparationResponse;
import com.lovespace.api.error.ApiException;
import com.lovespace.service.DataExportService;
import com.lovespace.service.DataExportService.ExportSnapshot;
import com.lovespace.time.BeijingTime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/data")
public class DataExportController {
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
        acquireSlot();
        try {
            ExportSnapshot snapshot = exports.prepare(auth);
            exports.scheduleCleanup(snapshot);
            return stream(snapshot);
        } catch (RuntimeException | IOException ex) {
            exportSlots.release();
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw ApiException.conflict("导出文件已无法读取，请重新准备导出。");
        }
    }

    @PostMapping("/export/prepare")
    public ExportPreparationResponse prepare(Authentication auth) {
        acquireSlot();
        try {
            ExportSnapshot snapshot = exports.register(exports.prepare(auth));
            exports.scheduleCleanup(snapshot);
            return new ExportPreparationResponse(
                    "/data/export/" + snapshot.token(), snapshot.filename(),
                    OffsetDateTime.ofInstant(snapshot.expiresAt(), BeijingTime.OFFSET));
        } finally {
            // Preparation has produced an immutable snapshot. Streaming is limited separately
            // when the one-time URL is claimed.
            exportSlots.release();
        }
    }

    @GetMapping(value = "/export/{token}", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> download(Authentication auth, @PathVariable String token) {
        acquireSlot();
        try {
            ExportSnapshot snapshot = exports.claim(auth, token);
            return stream(snapshot);
        } catch (RuntimeException | IOException ex) {
            exportSlots.release();
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw ApiException.conflict("导出文件已无法读取，请重新准备导出。");
        }
    }

    private void acquireSlot() {
        if (!exportSlots.tryAcquire()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_BUSY",
                    "已有数据导出正在进行，请稍后再试");
        }
    }

    private ResponseEntity<StreamingResponseBody> stream(ExportSnapshot snapshot) throws IOException {
        InputStream opened = null;
        long size;
        try {
            opened = exports.openSnapshot(snapshot);
            size = Files.size(snapshot.path());
        } catch (IOException ex) {
            try {
                if (opened != null) opened.close();
            } catch (IOException ignored) { }
            exports.deleteSnapshot(snapshot);
            throw ex;
        }
        final InputStream input = opened;
        StreamingResponseBody body = output -> {
            try (input) {
                input.transferTo(output);
            } finally {
                exports.deleteSnapshot(snapshot);
                exportSlots.release();
            }
        };
        return ResponseEntity.ok()
                .contentType(ZIP)
                .contentLength(size)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(snapshot.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
