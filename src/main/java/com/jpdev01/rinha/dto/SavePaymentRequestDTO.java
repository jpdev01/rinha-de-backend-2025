package com.jpdev01.rinha.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SavePaymentRequestDTO(
        String correlationId,
        BigDecimal amount,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime requestedAt
) implements Serializable {

    public SavePaymentRequestDTO {
        requestedAt = LocalDateTime.now().withNano(0);
    }

    public UUID correlationIdAsUUID() {
        return UUID.fromString(correlationId);
    }
}