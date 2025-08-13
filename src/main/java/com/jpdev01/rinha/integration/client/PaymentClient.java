package com.jpdev01.rinha.integration.client;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import reactor.core.publisher.Mono;

public interface PaymentClient {

    Mono<Boolean> create(SavePaymentRequestDTO savePaymentRequestDTO);

    Mono<HealthResponseDTO> health();

    boolean createSync(SavePaymentRequestDTO savePaymentRequestDTO);

    public default String toJson(SavePaymentRequestDTO request) {
        return """
                {
                  "correlationId": "%s",
                  "amount": %s,
                  "requestedAt": "%s"
                }
                """.formatted(
                escape(request.correlationId()),
                request.amount().toPlainString(),
                request.requestedAt().toString()
        ).replace("\n", "").replace("  ", "");
    }

    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
