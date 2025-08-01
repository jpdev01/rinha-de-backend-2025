package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.exception.PaymentProcessorException;
import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.client.FallBackClient;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import com.jpdev01.rinha.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentService {

    private final DefaultClient defaultClient;
    private final FallBackClient fallBackClient;
    private final PaymentRepository paymentRepository;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    public PaymentService(DefaultClient defaultClient, PaymentRepository paymentRepository, FallBackClient fallBackClient) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.paymentRepository = paymentRepository;

        subscribeQueue();
        subscribeHealthCheck();
    }

    public void subscribeHealthCheck() {
        final int period = 5;
        scheduler.scheduleAtFixedRate(this::checkDefaultHealth, 0, period, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkFallbackHealth, 0, period, TimeUnit.SECONDS);
    }

    private void checkDefaultHealth() {
        try {
            HealthResponseDTO healthResponseDTO = defaultClient.health().getBody();
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(isHealthy(healthResponseDTO));
        } catch (Exception e) {
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
        }
    }

    private boolean isHealthy(HealthResponseDTO healthResponseDTO) {
        if (healthResponseDTO == null) return false;
        if (healthResponseDTO.failing()) return false;
        if (healthResponseDTO.minResponseTime() > 0) return false;

        return true;
    }

    private void checkFallbackHealth() {
        try {
            HealthResponseDTO healthResponseDTO = fallBackClient.health().getBody();
            PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(isHealthy(healthResponseDTO));
        } catch (Exception e) {
            PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(false);
        }
    }

    public void subscribeQueue() {
        scheduler.scheduleAtFixedRate(this::processQueue, 0, 1, TimeUnit.MILLISECONDS);
    }

    public void process(SavePaymentRequestDTO savePaymentRequestDTO) {
        if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
            if (processWithDefault(savePaymentRequestDTO)) return;
        }

        if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
            if (processWithFallback(savePaymentRequestDTO)) return;
        }

        PaymentQueue.getInstance().add(savePaymentRequestDTO);
    }

    public void purge() {
        paymentRepository.purgeAll();
        PaymentProcessorHealthStatus.getInstance().reset();
        PaymentQueue.getInstance().queue.clear();
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponseDTO getPayments(LocalDateTime from, LocalDateTime to) {
        return paymentRepository.summary(from, to);
    }

    private void processQueue() {
        try {
            SavePaymentRequestDTO payment;
            while ((payment = PaymentQueue.getInstance().poll()) != null) {
                System.out.println("processQueue >> Processing payment: " + payment);
                if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
                    if (processWithDefault(payment)) {
                        PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(true);
                        return;
                    }
                }
                if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
                    if (processWithFallback(payment)) {
                        PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(true);
                        return;
                    }
                }
                PaymentQueue.getInstance().add(payment);
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar fila: " + e.getMessage());
        }
    }

    private Boolean processWithDefault(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            long start = System.nanoTime();
            defaultClient.create(savePaymentRequestDTO);
            if (isDelayed(start, System.nanoTime())) {
                System.out.println("processWithDefault >> Delayed default processing");
                PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            }
            paymentRepository.save(savePaymentRequestDTO, true);

            return true;
        } catch (PaymentProcessorException exception) {
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            return false;
        }
    }

    private Boolean processWithFallback(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            long start = System.nanoTime();
            fallBackClient.create(savePaymentRequestDTO);
            if (isDelayed(start, System.nanoTime())) {
                System.out.println("processWithFallback >> Delayed fallback processing");
                PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(false);
            }
            paymentRepository.save(savePaymentRequestDTO, false);

            return true;
        } catch (PaymentProcessorException exception) {
            PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(false);
            return false;
        }
    }

    private boolean isDelayed(long start, long end) {
        long durationMs = (end - start) / 1_000_000;
        return durationMs > 10;
    }
}
