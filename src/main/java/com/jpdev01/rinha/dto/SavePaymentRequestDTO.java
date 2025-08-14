package com.jpdev01.rinha.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jpdev01.rinha.Utils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record SavePaymentRequestDTO(
        String correlationId,
        BigDecimal amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant requestedAt,
        String json
) implements Serializable {

    public SavePaymentRequestDTO {
        requestedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        json = Utils.toJson(correlationId, amount, requestedAt);
    }

    public UUID correlationIdAsUUID() {
        return UUID.fromString(correlationId);
    }

    public static String toJson(String correlationId, BigDecimal amount, Instant requestedAt) {
        return """
                {
                  "correlationId": "%s",
                  "amount": %s,
                  "requestedAt": "%s"
                }
                """.formatted(
                escape(correlationId),
                amount.toPlainString(),
                requestedAt.toString()
        ).replace("\n", "").replace("  ", "");
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

}