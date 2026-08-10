package com.lovespace.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BeijingTimeTest {
    private TimeZone previous;

    @BeforeEach
    void captureDefaultZone() { previous = TimeZone.getDefault(); }

    @AfterEach
    void restoreDefaultZone() { TimeZone.setDefault(previous); }

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Shanghai", "UTC", "America/New_York"})
    void convertsMomentsToBeijingIndependentlyOfJvmDefaultZone(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        OffsetDateTime instant = OffsetDateTime.of(2025, 12, 31, 16, 30, 0, 0, ZoneOffset.UTC);

        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 30), BeijingTime.toLocal(instant));
        assertEquals(OffsetDateTime.parse("2026-01-01T00:30:00+08:00"),
                BeijingTime.toOffset(BeijingTime.toLocal(instant)));
    }
}
