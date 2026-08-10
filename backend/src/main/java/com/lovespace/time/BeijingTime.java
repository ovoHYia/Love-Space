package com.lovespace.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class BeijingTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    private BeijingTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static OffsetDateTime nowOffset() {
        return OffsetDateTime.now(OFFSET);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime toLocal(OffsetDateTime value) {
        return value == null ? null : value.toInstant().atZone(ZONE).toLocalDateTime();
    }

    public static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
    }
}
