package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.state.ClientRecoverSemaphore;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentAsyncService {

    private final PaymentService paymentService;
    private final DefaultClientState defaultClientState;
    private final ClientRecoverSemaphore clientSemaphore;

    private static final int BATCH_SIZE = 100;
    private final static int PARALLELISM = 20;
    private final static int MAX_RETRIES = 15;
    private final FallbackClientState fallbackClientState;

    public static final int MINIMUM_RESPONSE_TIME = 100;

    public PaymentAsyncService(DefaultClientState defaultClientState, PaymentService paymentService, FallbackClientState fallbackClientState, ClientRecoverSemaphore clientSemaphore) {
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;

        this.paymentService = paymentService;
        this.clientSemaphore = clientSemaphore;


        for (int i = 0; i < 20; i++) {
            System.out.println("Starting virtual thread " + i);
            Thread.startVirtualThread(this::runWorker);
        }

//        final int period = 10;
//        final int initialDelay = 100;
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
//        scheduler.scheduleAtFixedRate(this::processQueueBatch, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    private void runWorker() {
        while (true) {
            var payment = getPayment();
            if (defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME)) {
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    try {
                        boolean success = paymentService.processDefault(payment);
                        if (success) return;
                    } catch (Exception e) {
                        System.err.println("Erro ao processar pagamento com o cliente padrão: " + e.getMessage());
                    }
                }
            }

            if (defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME)) {
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    try {
                        boolean success = paymentService.processFallback(payment);
                        if (success) return;
                    } catch (Exception e) {
                        System.err.println("Erro ao processar pagamento com o cliente padrão: " + e.getMessage());
                    }
                }
            }

            PaymentQueue.getInstance().add(payment);
        }
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
