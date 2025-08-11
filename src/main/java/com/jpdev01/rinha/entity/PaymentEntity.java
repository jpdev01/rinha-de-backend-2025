package com.jpdev01.rinha.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("payments")
public class PaymentEntity {

    @Id
    private UUID correlationId;
    private BigDecimal amount;
    private LocalDateTime requestedAt;
    private Boolean processedAtDefault;

    // construtores, getters e setters
    public PaymentEntity(UUID correlationId, BigDecimal amount, LocalDateTime requestedAt, Boolean processedAtDefault) {
        this.correlationId = correlationId;
        this.amount = amount;
        this.requestedAt = requestedAt;
        this.processedAtDefault = processedAtDefault;
    }

    public PaymentEntity() {}
    
    // getters e setters...
}