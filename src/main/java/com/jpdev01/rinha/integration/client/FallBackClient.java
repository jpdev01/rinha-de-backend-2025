package com.jpdev01.rinha.integration.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.exception.PaymentProcessorException;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import com.jpdev01.rinha.integration.dto.SavePaymentResponseDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@Service
public class FallBackClient {

    @Value("${services.processor-fallback}")
    private String processorFallback;

    private RestClient restClient;
    private final ObjectMapper objectMapper;

    public FallBackClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder()
                .baseUrl(processorFallback)
                .build();
    }

    public boolean create(SavePaymentRequestDTO payment) throws PaymentProcessorException {
        try {
            restClient
                    .post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payment))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpServerErrorException.InternalServerError e) {
            throw new PaymentProcessorException("Error ao processar com fallback");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean safeCreate(SavePaymentRequestDTO payment) {
        try {
            restClient
                    .post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payment))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpServerErrorException.InternalServerError e) {
            return false;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing payment request", e);
        }
    }

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