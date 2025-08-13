package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.state.ClientRecoverSemaphore;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final FallbackClientState fallbackClientState;

    public static final int MINIMUM_RESPONSE_TIME = 100;

    public PaymentAsyncService(DefaultClientState defaultClientState, PaymentService paymentService, FallbackClientState fallbackClientState, ClientRecoverSemaphore clientSemaphore) {
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;

        this.paymentService = paymentService;
        this.clientSemaphore = clientSemaphore;

        final int period = 10;
        final int initialDelay = 100;
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        scheduler.scheduleAtFixedRate(this::processQueueBatch, initialDelay, period, TimeUnit.MILLISECONDS);
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
                .flatMap(this::processPaymentOrFail, PARALLELISM)
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
                        return Mono.error(new RuntimeException("Falha no processamento do pagamento"));
                    }
                    return Mono.just(true);
                });
    }

    private boolean isValid() {
        return defaultClientState.health() && defaultClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME)
                || fallbackClientState.health() && fallbackClientState.isMinimumResponseTimeUnder(MINIMUM_RESPONSE_TIME);
    }
}
