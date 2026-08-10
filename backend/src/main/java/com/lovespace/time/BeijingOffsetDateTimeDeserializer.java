package com.lovespace.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts the new offset-aware wire format and keeps old local datetime clients usable by
 * interpreting a missing offset as Beijing time.
 */
public final class BeijingOffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {
    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        String value = parser.getValueAsString();
        if (value == null) return (OffsetDateTime) context.handleUnexpectedToken(OffsetDateTime.class, parser);
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(value).atOffset(BeijingTime.OFFSET);
            } catch (RuntimeException invalid) {
                throw context.weirdStringException(value, OffsetDateTime.class,
                        "必须是带时区偏移的 ISO 日期时间");
            }
        }
    }
}
