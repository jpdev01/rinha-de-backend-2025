package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.entity.PaymentEntity;
import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.client.FallbackClient;
import com.jpdev01.rinha.integration.client.PaymentClient;
import com.jpdev01.rinha.repository.PaymentRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import static com.jpdev01.rinha.Utils.isDelayed;

@Service
public class PaymentService {

    private final DefaultClient defaultClient;
    private final FallbackClient fallBackClient;
    private final PaymentRepository paymentRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public PaymentService(DefaultClient defaultClient, PaymentRepository paymentRepository, FallbackClient fallBackClient, R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.paymentRepository = paymentRepository;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;

    }

    public Mono<Boolean> process(SavePaymentRequestDTO savePaymentRequestDTO) {
        if (PaymentProcessorState.getInstance().isDefaultProcessorHealthy()) {
            return processWithDefault(savePaymentRequestDTO);
        }
        if (PaymentProcessorState.getInstance().isFallbackProcessorHealthy()) {
            return processWithFallback(savePaymentRequestDTO);
        }
        return Mono.just(false);
    }

    public void purge() {
        paymentRepository.deleteAll();
        PaymentProcessorState.getInstance().reset();
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
                PaymentProcessorState.getInstance()::setDefaultProcessorHealthy
        );
    }

    private Mono<Boolean> processWithFallback(SavePaymentRequestDTO dto) {
        return callProcessor(
                dto,
                fallBackClient,
                false,
                PaymentProcessorState.getInstance()::setFallbackProcessorHealthy
        );
    }

    private Mono<Boolean> callProcessor(
            SavePaymentRequestDTO dto,
            PaymentClient client,
            boolean processedAtDefault,
            Consumer<Boolean> setProcessorHealth
    ) {
        final long start = System.nanoTime();

        return client.create(dto)
                .flatMap(success -> {
                    if (!success) {
                        setProcessorHealth.accept(false);
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
                                    setProcessorHealth.accept(false);
                                }
                                return Mono.just(true);
                            }));
                });
    }
}
