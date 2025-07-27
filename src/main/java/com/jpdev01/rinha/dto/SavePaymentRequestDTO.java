package com.jpdev01.rinha.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavePaymentRequestDTO(
        String correlationId,
        BigDecimal amount
) implements Serializable {

    public SavePaymentRequestDTO {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be a positive number");
        }
    }
}