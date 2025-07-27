package com.jpdev01.rinha.integration.dto;

import java.io.Serializable;

public record SavePaymentResponseDTO(
        String message
) implements Serializable {

    public SavePaymentResponseDTO {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or blank");
        }
    }
}