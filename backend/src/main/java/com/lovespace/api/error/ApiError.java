package com.lovespace.api.error;

import com.lovespace.time.BeijingTime;
import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(OffsetDateTime timestamp, int status, String code, String message,
                       Map<String, String> fieldErrors) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(BeijingTime.nowOffset(), status, code, message, null);
    }
}
