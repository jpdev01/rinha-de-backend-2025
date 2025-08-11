package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.state.ClientSemaphore;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.concurrent.BlockingQueue;

@Service
public class PaymentAsyncService {

    private final PaymentService paymentService;
    private final DefaultClientState defaultClientState;
    private final ClientSemaphore clientSemaphore;

    private static final int BATCH_SIZE = 5000;
    private final FallbackClientState fallbackClientState;

    public static final int MINIMUM_RESPONSE_TIME = 1000;

    public PaymentAsyncService(DefaultClientState defaultClientState, PaymentService paymentService, FallbackClientState fallbackClientState, ClientSemaphore clientSemaphore) {
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;

        this.paymentService = paymentService;
        this.clientSemaphore = clientSemaphore;

        final int period = 1;
        final int initialDelay = 1;
        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::processQueueBatch, initialDelay, period, java.util.concurrent.TimeUnit.SECONDS);
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
                                defaultClientState.setMinResponseTime(MINIMUM_RESPONSE_TIME);
                            } else {
                                 PaymentQueue.getInstance().add(queue.poll());
                            }
                        })
                        .subscribe();
                clientSemaphore.releaseDefault();
            } else if (clientSemaphore.tryAcquireFallback()) {
                paymentService.processWithFallback(queue.poll())
                        .doOnNext(success -> {
                            if (success) {
                                fallbackClientState.setHealthy(true);
                                fallbackClientState.setMinResponseTime(MINIMUM_RESPONSE_TIME);
                            } else {
                                PaymentQueue.getInstance().add(queue.poll());
                            }
                        })
                        .subscribe();
                clientSemaphore.releaseFallback();
            }
            return;
        }

        Flux.<SavePaymentRequestDTO>generate(sink -> {
                    SavePaymentRequestDTO payment = queue.poll();
                    if (payment == null || sink.currentContext().getOrDefault("count", 0) >= BATCH_SIZE) {
                        sink.complete();
                    } else {
                        sink.next(payment);
                    }
                })
                .index()  // Índice para contar quantos pagamentos já emitidos
                .takeWhile(tuple -> tuple.getT1() < BATCH_SIZE)  // Limita o fluxo ao tamanho do lote
                .map(Tuple2::getT2)  // Extrai o pagamento do Tuple2 (índice, pagamento)
                .concatMap(this::processPaymentOrFail)  // Processa um pagamento por vez
                .onErrorResume(e -> {
                    System.err.println("Erro ao processar pagamento: " + e.getMessage());
                    return Mono.empty();  // Interrompe o fluxo com log do erro
                })
                .subscribe();
    }

    private Mono<Boolean> processPaymentOrFail(SavePaymentRequestDTO payment) {
        return paymentService.process(payment, MINIMUM_RESPONSE_TIME)
                .flatMap(success -> {
                    if (!success) {
                        PaymentQueue.getInstance().add(payment);  // Reinsere pagamento na fila em caso de falha
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
