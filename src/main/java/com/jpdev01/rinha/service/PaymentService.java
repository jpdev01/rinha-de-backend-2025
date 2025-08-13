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

import java.time.Instant;
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

        try {
            testDbConnection();
        } catch (Exception exception) {
            System.err.println("Error initializing payment service: " + exception.getMessage());
        }
    }

    public boolean processDefault(SavePaymentRequestDTO payment) {
        boolean success = defaultClient.createSync(payment);
        if (success) {
            PaymentEntity entity = new PaymentEntity(
                    payment.correlationIdAsUUID(),
                    payment.amount(),
                    payment.requestedAt(),
                    true
            );
            r2dbcEntityTemplate.insert(entity).subscribe();
        } else {
            defaultClientState.setHealthy(false);
        }
        return success;
    }

    public boolean processFallback(SavePaymentRequestDTO payment) {
        boolean success = fallBackClient.createSync(payment);
        if (success) {
            PaymentEntity entity = new PaymentEntity(
                    payment.correlationIdAsUUID(),
                    payment.amount(),
                    payment.requestedAt(),
                    false
            );
            r2dbcEntityTemplate.insert(entity).block();
        } else {
            fallbackClientState.setHealthy(false);
        }
        return success;
    }

    public Mono<Boolean> process(SavePaymentRequestDTO savePaymentRequestDTO, long acceptableResponseTime) {
        boolean success = false;
//        if (defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(acceptableResponseTime)) {
////            for (int attempt = 0; attempt < 1; attempt++) {
////                try {
////                    success = processDefault(savePaymentRequestDTO);
////                    if (success) break;
////                } catch (Exception e) {
////                    System.err.println("Erro ao processar pagamento com o cliente padrão: " + e.getMessage());
////                }
////            }
//            success = processDefault(savePaymentRequestDTO);
//        }
//        if (success) return Mono.just(true);
//        if (fallbackClientState.health() && fallbackClientState.isMinimumResponseTimeUnder(acceptableResponseTime)) {
//            success = processFallback(savePaymentRequestDTO);
//        }

        if (!success) PaymentQueue.getInstance().getQueue().add(savePaymentRequestDTO);
        return Mono.just(true);
    }

    public Mono<Boolean> process(SavePaymentRequestDTO savePaymentRequestDTO) {
        return process(savePaymentRequestDTO, 10);
    }

    public void purge() {
        paymentRepository.deleteAll().subscribe();
        PaymentQueue.getInstance().queue.clear();
    }

    @Transactional(readOnly = true)
    public Mono<PaymentSummaryResponseDTO> getPayments(Instant from, Instant to) {
        return paymentRepository.summary(from, to);
    }

    public Mono<Boolean> processWithDefault(SavePaymentRequestDTO dto) {
        return callProcessor(
                dto,
                defaultClient,
                true,
                defaultClientState
        );
    }

    public Mono<Boolean> processWithFallback(SavePaymentRequestDTO dto) {
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
                                int diff = (int) ((System.nanoTime() - start) / 1_000_000);
                                clientState.setMinResponseTime(diff);
                                return Mono.just(true);
                            }));
                })
                .onErrorResume(e -> {
                    System.err.println("Erro no fluxo: " + e.getMessage());
                    return Mono.just(false);
                });
    }

    private void testDbConnection() {
        r2dbcEntityTemplate.getDatabaseClient()
                .sql("SELECT 1")
                .fetch()
                .first()
                .doOnError(e -> {
                    throw new RuntimeException("Failed to connect to the database", e);
                })
                .doOnSuccess(result -> {
                    System.out.println("Database connection is healthy");
                })
                .subscribe();
    }
}
