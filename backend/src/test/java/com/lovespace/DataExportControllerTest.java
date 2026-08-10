package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lovespace.api.DataExportController;
import com.lovespace.api.error.ApiException;
import com.lovespace.service.DataExportService;
import com.lovespace.service.DataExportService.ExportSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class DataExportControllerTest {
    @Test
    void keepsTheConcurrentSlotUntilStreamingFinishesAndCleansOnDisconnect() throws Exception {
        Path snapshotPath = Files.createTempFile("love-space-controller-test-", ".zip");
        byte[] contents = new byte[]{1, 2, 3, 4};
        Files.write(snapshotPath, contents);
        try {
            DataExportService exports = mock(DataExportService.class);
            Authentication auth = mock(Authentication.class);
            ExportSnapshot snapshot = new ExportSnapshot(snapshotPath, 1L, 2L, "export.zip",
                    Instant.now().plusSeconds(600));
            when(exports.prepare(any())).thenReturn(snapshot);
            when(exports.openSnapshot(snapshot)).thenAnswer(ignored -> Files.newInputStream(snapshotPath));
            DataExportController controller = new DataExportController(exports, 1);

            ResponseEntity<StreamingResponseBody> first = controller.export(auth);
            assertNotNull(first.getBody());
            ApiException busy = assertThrows(ApiException.class, () -> controller.export(auth));
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, busy.getStatus());

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            first.getBody().writeTo(output);
            assertArrayEquals(contents, output.toByteArray());

            ResponseEntity<StreamingResponseBody> next = controller.export(auth);
            assertNotNull(next.getBody());
            OutputStream disconnected = new OutputStream() {
                @Override public void write(int value) throws IOException { throw new IOException("client disconnected"); }
            };
            assertThrows(IOException.class, () -> next.getBody().writeTo(disconnected));
            verify(exports, times(2)).deleteSnapshot(snapshot);
        } finally {
            Files.deleteIfExists(snapshotPath);
        }
    }
}
