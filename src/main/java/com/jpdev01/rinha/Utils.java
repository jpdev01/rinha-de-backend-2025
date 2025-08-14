package com.jpdev01.rinha;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Utils {

    public static boolean isDelayed(long start, long end) {
        long durationMs = (end - start) / 1_000_000;
        return durationMs > 10;
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static String toJson(String correlationId, BigDecimal amount, Instant requestedAt) {
        return """
                {"correlationId":"%s","amount":%s,"requestedAt":"%s"}
                """.formatted(
                escape(correlationId),
                amount != null ? amount.toPlainString() : "null",
                requestedAt != null ? requestedAt.atOffset(ZoneOffset.UTC).format(DATE_FORMATTER) : "null"
        );
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
