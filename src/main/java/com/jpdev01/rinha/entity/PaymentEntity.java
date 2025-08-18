package com.jpdev01.rinha.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentEntity {

    private UUID correlationId;
    private BigDecimal amount;
    private Instant requestedAt;
    private boolean processedAtDefault;

    public PaymentEntity(UUID correlationId, BigDecimal amount, Instant requestedAt, boolean processedAtDefault) {
        this.correlationId = correlationId;
        this.amount = amount;
        this.requestedAt = requestedAt;
        this.processedAtDefault = processedAtDefault;
    }

    public PaymentEntity() {}

    public UUID getCorrelationId() {
        return correlationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public boolean isProcessedAtDefault() {
        return processedAtDefault;
    }
}