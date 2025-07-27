package com.jpdev01.rinha.integration.dto;

import java.io.Serializable;

public record HealthResponseDTO(
        Boolean failing,
        Integer minResponseTime
) implements Serializable {

    private static final long serialVersionUID = - 4392413525500419319L;

    public HealthResponseDTO {
        if (failing == null) {
            throw new IllegalArgumentException("Failing cannot be null");
        }
        if (minResponseTime == null || minResponseTime < 0) {
            throw new IllegalArgumentException("Min response time must be a non-negative integer");
        }
    }
}