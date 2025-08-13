package com.jpdev01.rinha.integration.client;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class DefaultClient implements PaymentClient {

    @Value("${services.processor-default}")
    private String processorDefault;

    private WebClient defaultWebClient;

    public DefaultClient(WebClient defaultWebClient) {
        this.defaultWebClient = defaultWebClient;
    }

    public Mono<Boolean> create(SavePaymentRequestDTO payment) {
        return defaultWebClient.post()
                .uri("/payments")
                .bodyValue(toJson(payment))
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofMillis(200))
                .flatMap(resp -> {
                    if (resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
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
    public Mono<HealthResponseDTO> health() {
        return defaultWebClient.get()
                .uri("/payments/service-health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(HealthResponseDTO.class)
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> {
                    HealthResponseDTO fallback = new HealthResponseDTO(true, 1000);
                    return Mono.just(fallback);
                });
    }
}