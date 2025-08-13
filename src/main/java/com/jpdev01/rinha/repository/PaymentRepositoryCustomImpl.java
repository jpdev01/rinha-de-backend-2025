package com.jpdev01.rinha.repository;

import com.jpdev01.rinha.dto.PaymentProcessorSummaryDTO;
import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public class PaymentRepositoryCustomImpl implements PaymentRepositoryCustom {

    private final DatabaseClient databaseClient;

    public PaymentRepositoryCustomImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<PaymentSummaryResponseDTO> summary(Instant from, Instant to) {
        String sql = """
        SELECT
            COUNT(*) AS total,
            SUM(amount) AS total_amount,
            processed_at_default
        FROM payments
        WHERE requested_at BETWEEN :from AND :to
        GROUP BY processed_at_default
        """;

        return databaseClient.sql(sql)
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> new PaymentProcessorSummaryDTO(
                        row.get("total", Integer.class),
                        row.get("total_amount", BigDecimal.class),
                        row.get("processed_at_default", Boolean.class)
                ))
                .all()
                .collectList()
                .map(list -> {
                    PaymentProcessorSummaryDTO defaultSummary = new PaymentProcessorSummaryDTO(0, BigDecimal.ZERO, true);
                    PaymentProcessorSummaryDTO fallbackSummary = new PaymentProcessorSummaryDTO(0, BigDecimal.ZERO, false);

                    for (PaymentProcessorSummaryDTO dto : list) {
                        if (Boolean.TRUE.equals(dto.processedAtDefault())) {
                            defaultSummary = dto;
                        } else {
                            fallbackSummary = dto;
                        }
                    }
                    return new PaymentSummaryResponseDTO(defaultSummary, fallbackSummary);
                });
    }
}
