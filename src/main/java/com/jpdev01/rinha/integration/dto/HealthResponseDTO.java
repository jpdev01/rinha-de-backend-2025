package com.jpdev01.rinha.integration.dto;

import java.io.Serializable;

public record HealthResponseDTO(
        boolean failing,
        int minResponseTime
) implements Serializable {
}