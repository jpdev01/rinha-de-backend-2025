package com.jpdev01.rinha.integration.client;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface PaymentClient {

    Mono<Boolean> create(SavePaymentRequestDTO savePaymentRequestDTO);

    Mono<ResponseEntity<HealthResponseDTO>> health();
}
