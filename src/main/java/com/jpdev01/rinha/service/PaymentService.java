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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    private final DefaultClient defaultClient;
    private final FallbackClient fallBackClient;
    private final DefaultClientState defaultClientState;
    private final FallbackClientState fallbackClientState;
    private final PaymentRepository paymentRepository;

    public PaymentService(DefaultClient defaultClient, PaymentRepository paymentRepository, FallbackClient fallBackClient, DefaultClientState defaultClientState, FallbackClientState fallbackClientState) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;
        this.paymentRepository = paymentRepository;

//        try {
//            testDbConnection();
//        } catch (Exception exception) {
//            System.err.println("Error initializing payment service: " + exception.getMessage());
//        }
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
            paymentRepository.insert(entity);
        } else {
            defaultClientState.setHealthy(false);
        }
        return success;
    }

//    public void insert(List<PaymentEntity> paymentList) {
//        Flux.fromIterable(paymentList)
//                .flatMap(this::insertPagamento, 20)
//                .doOnError(e -> System.err.println("Erro ao inserir batch: " + e.getMessage()))
//                .subscribe(); // <<< muito importante
//    }

//    private Mono<PaymentEntity> insertPagamento(PaymentEntity payment) {
//        return r2dbcEntityTemplate.insert(payment)
//                .doOnError(e -> System.err.println("Erro ao inserir pagamento: " + e.getMessage()));
//    }

    public boolean processFallback(SavePaymentRequestDTO payment) {
        boolean success = fallBackClient.createSync(payment);
        if (success) {
            PaymentEntity entity = new PaymentEntity(
                    payment.correlationIdAsUUID(),
                    payment.amount(),
                    payment.requestedAt(),
                    false
            );
            paymentRepository.insert(entity);
//            PaymentQueue.getInstance().addToInsertQueue(entity);
//            r2dbcEntityTemplate.insert(entity).block();
        } else {
            fallbackClientState.setHealthy(false);
        }
        return success;
    }

    public void add(SavePaymentRequestDTO payment) {
        PaymentQueue.getInstance().getQueue().add(payment);
    }

    public Mono<Boolean> process(SavePaymentRequestDTO savePaymentRequestDTO, long acceptableResponseTime) {
//        if (defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(0)) {
//            return processWithDefault(savePaymentRequestDTO);
//        }

        long start = System.nanoTime();
        PaymentQueue.getInstance().add(savePaymentRequestDTO);
        long duration = System.nanoTime() - start;
        if (duration > 1_000_000) {
            System.err.println("add took too long: " + duration / 1_000_000 + " ms");
        }
        return Mono.just(true);
    }

    public Mono<Boolean> process(SavePaymentRequestDTO savePaymentRequestDTO) {
        return process(savePaymentRequestDTO, 10);
    }

    public void purge() {
        paymentRepository.deleteAll();
        PaymentQueue.getInstance().queue.clear();
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponseDTO getPayments(Instant from, Instant to) {
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
                        if (clientState instanceof DefaultClientState) {
                            PaymentQueue.getInstance().addToDefaultRetry(dto);
                        } else if (clientState instanceof FallbackClientState) {
                            PaymentQueue.getInstance().addToFallbackRetry(dto);
                        }
                        return Mono.just(true);
                    }

                    PaymentEntity entity = new PaymentEntity(
                            dto.correlationIdAsUUID(),
                            dto.amount(),
                            dto.requestedAt(),
                            processedAtDefault
                    );

                    paymentRepository.insert(entity);
//                    return r2dbcEntityTemplate.insert(entity)
//                            .then(Mono.defer(() -> {
//                                int diff = (int) ((System.nanoTime() - start) / 1_000_000);
//                                clientState.setMinResponseTime(diff);
//                                return Mono.just(true);
//                            }));
                    return Mono.just(true);
                })
                .onErrorResume(e -> {
                    System.err.println("Erro no fluxo: " + e.getMessage());
                    return Mono.just(false);
                });
    }

//    private void testDbConnection() {
//        r2dbcEntityTemplate.getDatabaseClient()
//                .sql("SELECT 1")
//                .fetch()
//                .first()
//                .doOnError(e -> {
//                    throw new RuntimeException("Failed to connect to the database", e);
//                })
//                .doOnSuccess(result -> {
//                    System.out.println("Database connection is healthy");
//                })
//                .subscribe();
//    }
}
