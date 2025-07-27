package com.jpdev01.rinha.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

public record PaymentProcessorSummaryDTO(int totalRequests, BigDecimal totalAmount, @JsonIgnore  Boolean processedAtDefault) {
}