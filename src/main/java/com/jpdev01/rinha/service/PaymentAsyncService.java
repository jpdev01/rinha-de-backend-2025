package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.exception.PaymentProcessorException;
import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.client.FallBackClient;
import com.jpdev01.rinha.repository.PaymentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.jpdev01.rinha.Utils.isDelayed;

@Service
public class PaymentAsyncService {

    private final DefaultClient defaultClient;
    private final FallBackClient fallBackClient;
    private final PaymentRepository paymentRepository;
    private final PaymentBatchService paymentBatchService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final int maxThreadsQueueSize = 100;
    private final ExecutorService workerPool = new ThreadPoolExecutor(
            5, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(maxThreadsQueueSize)
    );

    private static final int BATCH_SIZE = 50;


    public PaymentAsyncService(DefaultClient defaultClient, PaymentRepository paymentRepository, FallBackClient fallBackClient, PaymentBatchService paymentBatchService) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.paymentRepository = paymentRepository;
        this.paymentBatchService = paymentBatchService;

        subscribeQueue();
    }

    private void subscribeQueue() {
        scheduler.scheduleAtFixedRate(this::processQueueBatch, 0, 2, TimeUnit.MILLISECONDS);
    }

    private void processQueueBatch() {
        if (!PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) return;

        BlockingQueue<SavePaymentRequestDTO> queue = PaymentQueue.getInstance().getQueue();
        while (!queue.isEmpty()) {
            List<SavePaymentRequestDTO> batch = new ArrayList<>(BATCH_SIZE);
            queue.drainTo(batch, BATCH_SIZE);

            if (!batch.isEmpty()) {
                workerPool.submit(() -> {
                    try {
                        long start = System.nanoTime();
                        paymentBatchService.processBatchWithDefault(batch);
                        long end = System.nanoTime();
                        long delay = TimeUnit.NANOSECONDS.toMillis(end - start);
                        System.out.println("Batch processed in " + delay + " ms");
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    public void processBatchWithFallback(List<SavePaymentRequestDTO> batch) {
        for (SavePaymentRequestDTO payment : batch) {
            if (processWithFallback(payment)) {
                batch.remove(payment);
                continue;
            }
            break;
        }
        PaymentQueue.getInstance().getQueue().addAll(batch);
    }

    private void processBatchWithDefault(List<SavePaymentRequestDTO> batch) {
        for (SavePaymentRequestDTO payment : batch) {
            if (processWithDefault(payment)) {
                batch.remove(payment);
                continue;
            }
            break;
        }
        PaymentQueue.getInstance().getQueue().addAll(batch);
    }

    private boolean shouldUseDefault() {
        return PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy() ||
               PaymentProcessorHealthStatus.getInstance().shouldCheckDefaultProcessor();
    }

    private boolean shouldUseFallback() {
        return PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy() ||
               PaymentProcessorHealthStatus.getInstance().shouldCheckFallbackProcessor();
    }

    private void processDLQ() {
        try {
            if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
                readDLQ(payment -> {
                    if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
                        if (processWithFallback(payment)) {
                            return;
                        }
                    }
                    if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
                        PaymentQueue.getInstance().add(payment);
                    }
                });
                return;
            }
            SavePaymentRequestDTO payment = PaymentQueue.getInstance().pollDLQ();
            if (payment != null) {
                processWithFallback(payment);
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar DLQ: " + e.getMessage());
        }
    }

    private void readQueue(Consumer<SavePaymentRequestDTO> consumer) {
        SavePaymentRequestDTO payment;
        while ((payment = PaymentQueue.getInstance().poll()) != null) {
            consumer.accept(payment);
        }
    }

    private void readDLQ(Consumer<SavePaymentRequestDTO> consumer) {
        SavePaymentRequestDTO payment;
        while ((payment = PaymentQueue.getInstance().pollDLQ()) != null) {
            consumer.accept(payment);
        }
    }

    private boolean processWithDefault(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            long start = System.nanoTime();
            boolean success = defaultClient.create(savePaymentRequestDTO);
            if (!success) return false;
            if (isDelayed(start, System.nanoTime())) {
                PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            }
            paymentRepository.save(savePaymentRequestDTO, true);

            return true;
        } catch (PaymentProcessorException exception) {
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            return false;
        }
    }

    private boolean processWithFallback(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            long start = System.nanoTime();

            boolean success = fallBackClient.create(savePaymentRequestDTO);
            if (!success) return false;

            if (isDelayed(start, System.nanoTime())) {
                PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(false);
            }
            paymentRepository.save(savePaymentRequestDTO, false);

            return true;
        } catch (PaymentProcessorException exception) {
            PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(false);
            return false;
        }
    }
}
