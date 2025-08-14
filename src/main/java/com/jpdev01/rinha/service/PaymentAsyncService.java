package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.entity.PaymentEntity;
import com.jpdev01.rinha.state.ClientRecoverSemaphore;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

@Service
public class PaymentAsyncService {

    private final PaymentService paymentService;
    private final DefaultClientState defaultClientState;
    private final ClientRecoverSemaphore clientSemaphore;

    private static final int BATCH_SIZE = 100;
    private final static int PARALLELISM = 10;
    private final static int MAX_RETRIES = 15;
    private final FallbackClientState fallbackClientState;

    public static final int MINIMUM_RESPONSE_TIME = 100;

    public PaymentAsyncService(DefaultClientState defaultClientState, PaymentService paymentService, FallbackClientState fallbackClientState, ClientRecoverSemaphore clientSemaphore) {
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;

        this.paymentService = paymentService;
        this.clientSemaphore = clientSemaphore;


        for (int i = 0; i < PARALLELISM; i++) {
            Thread.startVirtualThread(this::runWorker);
        }
        for (int i = 0; i < 2; i++) {
            Thread.startVirtualThread(this::runDefaultWorker);
        }
        for (int i = 0; i < 5; i++) {
            Thread.startVirtualThread(this::runInsertWorker);
        }
    }

    private void runWorker() {
        while (true) {
            var payment = getPayment();
            if (defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME)) {
                if (processDefaultWithRetry(payment)) continue;

                PaymentQueue.getInstance().addToDefaultRetry(payment);
                continue;
            }

            if (fallbackClientState.health() && fallbackClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME)) {
                if (processFallbackWithRetry(payment)) continue;

                PaymentQueue.getInstance().addToFallbackRetry(payment);
                continue;
            }

            PaymentQueue.getInstance().add(payment);
        }
    }

    private void runInsertWorker() {
        while (true) {
            try {
                var payment = getInsertPayment();
                if (payment.isEmpty()) continue;
                paymentService.insert(payment);
            } catch (Exception e) {
                System.err.println("Erro ao processar inserção de pagamentos: " + e.getMessage());
            }
        }
    }

    public SavePaymentRequestDTO getDefaultPayment() {
        try {
            return PaymentQueue.getInstance().getDefaultQueue().take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void runDefaultWorker() {
        while (true) {
            var payment = getDefaultPayment();
            if (defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME)) {
                if (processDefaultWithRetry(payment)) continue;
            }
            PaymentQueue.getInstance().addToDefaultRetry(payment);
        }
    }

    private boolean processDefaultWithRetry(SavePaymentRequestDTO payment) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                boolean success = paymentService.processDefault(payment);
                if (success) return true;
            } catch (Exception e) {
                System.err.println("Erro ao processar pagamento com o cliente padrão: " + e.getMessage());
            }
        }
        return false;
    }

    public List<PaymentEntity> getInsertPayment() {
        try {
            List<PaymentEntity> list = new ArrayList<>();
            PaymentQueue.getInstance().getInsertQueue().drainTo(list, 100);
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean processFallbackWithRetry(SavePaymentRequestDTO payment) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                boolean success = paymentService.processFallback(payment);
                if (success) return true;
            } catch (Exception e) {
                System.err.println("Erro ao processar pagamento com o cliente padrão: " + e.getMessage());
            }
        }
        return false;
    }

    public SavePaymentRequestDTO getPayment() {
        try {
            return PaymentQueue.getInstance().getQueue().take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void processQueueBatch() {
        BlockingQueue<SavePaymentRequestDTO> queue = PaymentQueue.getInstance().getQueue();
        if (queue.isEmpty()) return;

        if (!isValid()) {
            if (clientSemaphore.tryAcquireDefault()) {
                paymentService.processWithDefault(queue.poll())
                        .doOnNext(success -> {
                            if (success) {
                                defaultClientState.setHealthy(true);
                            }
                        })
                        .subscribe();
                clientSemaphore.releaseDefault();
            } else if (clientSemaphore.tryAcquireFallback()) {
                paymentService.processWithFallback(queue.poll())
                        .doOnNext(success -> {
                            if (success) {
                                fallbackClientState.setHealthy(true);
                            }
                        })
                        .subscribe();
                clientSemaphore.releaseFallback();
            }
            return;
        }

        Flux.<SavePaymentRequestDTO>generate(sink -> {
                    SavePaymentRequestDTO payment = queue.poll();
                    if (payment == null) {
                        sink.complete();
                    } else {
                        sink.next(payment);
                    }
                })
                .take(BATCH_SIZE)
                .flatMap(payment ->
                                processPaymentOrFail(payment)
                                        .retryWhen(Retry.fixedDelay(3, Duration.ofMillis(10)))
                                        .onErrorResume(e -> {
                                            System.err.println("Falha após retries para pagamento " + payment + ": " + e.getMessage());
                                            PaymentQueue.getInstance().add(payment);
                                            return Mono.empty();
                                        }),
                        PARALLELISM
                )
                .onErrorResume(e -> {
                    System.err.println("Erro ao processar pagamento: " + e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<Boolean> processPaymentOrFail(SavePaymentRequestDTO payment) {
        return paymentService.process(payment, MINIMUM_RESPONSE_TIME)
                .flatMap(success -> {
                    if (!success) {
//                        PaymentQueue.getInstance().add(payment);
                        return Mono.just(false);
                    }
                    return Mono.just(true);
                })
                .onErrorResume(e -> {
                    System.err.println("Erro no fluxo: " + e.getMessage());
                    return Mono.just(false);
                });
    }

    private boolean isValid() {
        return defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME)
                || fallbackClientState.health() && fallbackClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME);
    }
}
