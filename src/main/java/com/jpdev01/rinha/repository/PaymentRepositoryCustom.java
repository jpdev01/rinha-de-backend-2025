package com.jpdev01.rinha.repository;

import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;

public interface PaymentRepositoryCustom {
    Mono<PaymentSummaryResponseDTO> summary(Instant from, Instant to);
}