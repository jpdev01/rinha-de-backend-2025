package com.jpdev01.rinha.integration.client;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

@Service
public class FallbackClient implements PaymentClient {

    @Value("${services.processor-fallback}")
    private String processorFallback;

    private final WebClient fallbackWebClient;
    private final HttpClient httpClient;

    public FallbackClient(HttpClient httpClient, WebClient fallbackWebClient) {
        this.httpClient = httpClient;
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
    public boolean createSync(SavePaymentRequestDTO paymentRequestDTO) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .timeout(ofSeconds(20))
                    .uri(URI.create(processorFallback + "/payments"))
                    .POST(ofString(toJson(paymentRequestDTO)))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return true;
            if (response.statusCode() == 422) {
                System.out.println("Payment request failed with status 422");
                return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public Mono<HealthResponseDTO> health() {
        return fallbackWebClient.get()
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