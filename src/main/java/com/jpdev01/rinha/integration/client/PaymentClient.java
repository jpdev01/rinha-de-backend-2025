package com.jpdev01.rinha.integration.client;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import reactor.core.publisher.Mono;

public interface PaymentClient {

    Mono<Boolean> create(SavePaymentRequestDTO savePaymentRequestDTO);
}
