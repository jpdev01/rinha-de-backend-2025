package com.jpdev01.rinha.integration.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.exception.PaymentProcessorException;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import com.jpdev01.rinha.integration.dto.SavePaymentResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@Service
public class DefaultClient {

    @Value("${services.processor-default}")
    private String processorDefault;

    private final ObjectMapper objectMapper;

    public DefaultClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<SavePaymentResponseDTO> process(SavePaymentRequestDTO payment) throws PaymentProcessorException {
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(processorDefault)
                    .build();

            return restClient
                    .post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payment))
                    .retrieve()
                    .toEntity(SavePaymentResponseDTO.class);
        } catch (HttpServerErrorException.InternalServerError e) {
            throw new PaymentProcessorException("Error ao processar com default");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public ResponseEntity<HealthResponseDTO> health() {
        RestClient restClient = RestClient.builder()
                .baseUrl(processorDefault)
                .build();

        return restClient
                .get()
                .uri("/payments/service-health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(HealthResponseDTO.class);

    }
}