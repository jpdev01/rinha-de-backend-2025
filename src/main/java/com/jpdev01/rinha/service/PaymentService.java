package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.entity.PaymentEntity;
import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.client.FallbackClient;
import com.jpdev01.rinha.integration.client.PaymentClient;
import com.jpdev01.rinha.repository.PaymentRepository;
import com.jpdev01.rinha.state.ClientState;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static com.jpdev01.rinha.Utils.isDelayed;

@Service
public class PaymentService {

    private final DefaultClient defaultClient;
    private final FallbackClient fallBackClient;
    private final DefaultClientState defaultClientState;
    private final FallbackClientState fallbackClientState;
    private final PaymentRepository paymentRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public PaymentService(DefaultClient defaultClient, PaymentRepository paymentRepository, FallbackClient fallBackClient, R2dbcEntityTemplate r2dbcEntityTemplate, DefaultClientState defaultClientState, FallbackClientState fallbackClientState) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;
        this.paymentRepository = paymentRepository;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }

    public Mono<Boolean> process(SavePaymentRequestDTO savePaymentRequestDTO) {
        if (defaultClientState.health() || defaultClientState.acquireRetry()) {
            return processWithDefault(savePaymentRequestDTO);
        }
        if (fallbackClientState.health() || fallbackClientState.acquireRetry()) {
            return processWithFallback(savePaymentRequestDTO);
        }
        return Mono.just(false);
    }

    public void purge() {
        paymentRepository.deleteAll();
        defaultClientState.setHealthy(false);
        fallbackClientState.setHealthy(false);
        PaymentQueue.getInstance().queue.clear();
    }

    @Transactional(readOnly = true)
    public Mono<PaymentSummaryResponseDTO> getPayments(LocalDateTime from, LocalDateTime to) {
        return paymentRepository.summary(from, to);
    }

    private Mono<Boolean> processWithDefault(SavePaymentRequestDTO dto) {
        return callProcessor(
                dto,
                defaultClient,
                true,
                defaultClientState
        );
    }

    private Mono<Boolean> processWithFallback(SavePaymentRequestDTO dto) {
        return callProcessor(
                dto,
                fallBackClient,
                false,
                fallbackClientState
        );
    }

    private Mono<Boolean> callProcessor(
            SavePaymentRequestDTO dto,
            PaymentClient client,
            boolean processedAtDefault,
            ClientState clientState
    ) {
        final long start = System.nanoTime();

        return client.create(dto)
                .flatMap(success -> {
                    if (!success) {
                        clientState.setHealthy(false);
                        return Mono.just(false);
                    }

                    PaymentEntity entity = new PaymentEntity(
                            dto.correlationIdAsUUID(),
                            dto.amount(),
                            dto.requestedAt(),
                            processedAtDefault
                    );

                    return r2dbcEntityTemplate.insert(entity)
                            .then(Mono.defer(() -> {
                                if (isDelayed(start, System.nanoTime())) {
                                    clientState.setHealthy(false);
                                }
                                return Mono.just(true);
                            }));
                });
    }
}
