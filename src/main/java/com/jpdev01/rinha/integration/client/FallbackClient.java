package com.jpdev01.rinha.integration.client;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class FallbackClient implements PaymentClient {

    @Value("${services.processor-fallback}")
    private String processorFallback;

    private WebClient fallbackWebClient;

    public FallbackClient(WebClient fallbackWebClient) {
        this.fallbackWebClient = fallbackWebClient;
    }

    public Mono<Boolean> create(SavePaymentRequestDTO payment) {
        return fallbackWebClient.post()
                .uri("/payments")
                .bodyValue(payment)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofMillis(200))
                .flatMap(resp -> {
                    if (resp.getStatusCode().is2xxSuccessful()) {
                        return Mono.just(true);
                    } else {
                        return Mono.just(false);
                    }
                })
                .onErrorResume(e -> {
                    // Lidar com erros HTTP ou de banco
                    return Mono.just(false);
                });
    }

    @Override
    public ResponseEntity<HealthResponseDTO> health() {
        RestClient restClient = RestClient.builder()
                .baseUrl(processorFallback)
                .build();

        return restClient
                .get()
                .uri("/payments/service-health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(HealthResponseDTO.class);
    }
}