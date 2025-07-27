package com.jpdev01.rinha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentSummaryResponseDTO(@JsonProperty("default") PaymentProcessorSummaryDTO defaultSummary,
                                        @JsonProperty("fallback") PaymentProcessorSummaryDTO fallbackSummary) {
}