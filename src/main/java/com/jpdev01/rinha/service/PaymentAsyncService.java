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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.jpdev01.rinha.Utils.isDelayed;

@Transactional
@Service
public class PaymentAsyncService {

    private final DefaultClient defaultClient;
    private final FallBackClient fallBackClient;
    private final PaymentRepository paymentRepository;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public PaymentAsyncService(DefaultClient defaultClient, PaymentRepository paymentRepository, FallBackClient fallBackClient) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.paymentRepository = paymentRepository;
        // subscribeQueue();
        scheduler.scheduleWithFixedDelay(this::checkQueue, 0, 1, TimeUnit.MILLISECONDS);

    }

    private final ExecutorService workerPool = Executors.newFixedThreadPool(10);

    private void checkQueue() {
        List<SavePaymentRequestDTO> batch = new ArrayList<>();

        SavePaymentRequestDTO payment;
        while ((payment = PaymentQueue.getInstance().poll()) != null) {
            batch.add(payment);
        }
        if (!batch.isEmpty()) {
            workerPool.submit(() -> {
                for (SavePaymentRequestDTO p : batch) {
                    boolean success = processWithDefault(p);
                    redriveIfNecessary(p, success, true);
                }
            });
        }
    }

    private void dlqWorkerLoop() {
        while (true) {
            SavePaymentRequestDTO payment = PaymentQueue.getInstance().pollDLQ();
            if (payment != null) {
                processWithFallback(payment);
            } else {
                try {
                    Thread.sleep(1); // evite busy-wait
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void redriveIfNecessary(SavePaymentRequestDTO payment, boolean result, boolean isDefault) {
        if (result) return;
        if (isDefault) {
            if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
                PaymentQueue.getInstance().addToDLQ(payment);
            }
            return;
        }

        if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
            PaymentQueue.getInstance().add(payment);
        }
    }

//    private void subscribeQueue() {
//        scheduler.scheduleAtFixedRate(this::processQueue, 0, 1, TimeUnit.MILLISECONDS);
//        scheduler.scheduleAtFixedRate(this::processDLQ, 0, 1, TimeUnit.MILLISECONDS);
//    }
//
//    private void processQueue() {
//        try {
//            if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
//                readQueue(payment -> {
//                    if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
//                        if (processWithDefault(payment)) {
//                            return;
//                        }
//                    }
//                    if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
//                        PaymentQueue.getInstance().addToDLQ(payment);
//                    }
//                });
//                return;
//            }
//            SavePaymentRequestDTO payment = PaymentQueue.getInstance().poll();
//            if (payment != null) {
//                processWithDefault(payment);
//            }
//        } catch (Exception e) {
//            System.err.println("Erro ao processar fila: " + e.getMessage());
//        }
//    }
//
//    private void processDLQ() {
//        try {
//            if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
//                readDLQ(payment -> {
//                    if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
//                        if (processWithFallback(payment)) {
//                            return;
//                        }
//                    }
//                    if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
//                        PaymentQueue.getInstance().add(payment);
//                    }
//                });
//                return;
//            }
//            SavePaymentRequestDTO payment = PaymentQueue.getInstance().pollDLQ();
//            if (payment != null) {
//                processWithFallback(payment);
//            }
//        } catch (Exception e) {
//            System.err.println("Erro ao processar DLQ: " + e.getMessage());
//        }
//    }
//
//    private void readQueue(Consumer<SavePaymentRequestDTO> consumer) {
//        SavePaymentRequestDTO payment;
//        while ((payment = PaymentQueue.getInstance().poll()) != null) {
//            consumer.accept(payment);
//        }
//    }
//
//    private void readDLQ(Consumer<SavePaymentRequestDTO> consumer) {
//        SavePaymentRequestDTO payment;
//        while ((payment = PaymentQueue.getInstance().pollDLQ()) != null) {
//            consumer.accept(payment);
//        }
//    }

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
