package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.exception.PaymentProcessorException;
import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.client.FallBackClient;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import com.jpdev01.rinha.repository.PaymentRepository;
import org.springframework.stereotype.Service;

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
        scheduler.scheduleAtFixedRate(this::processQueue, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void process(SavePaymentRequestDTO savePaymentRequestDTO) {
        if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
            if (processWithDefault(savePaymentRequestDTO)) return;
            System.out.println("failed default");
        }

        if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
            if (processWithFallback(savePaymentRequestDTO)) return;
            System.out.println("failed fallback");
        }

        System.out.println("process >> Both processors are unhealthy, adding payment to queue for later processing.");
        throw new RuntimeException("Off");
        // PaymentQueue.getInstance().add(savePaymentRequestDTO);
    }

    public void purge() {
        paymentRepository.purgeAll();
    }

    public PaymentSummaryResponseDTO getPayments(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Os parâmetros 'from' e 'to' não podem ser nulos.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' deve ser anterior a 'to'.");
        }
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
            defaultClient.create(savePaymentRequestDTO);
            paymentRepository.save(savePaymentRequestDTO, true);

            return true;
        } catch (PaymentProcessorException exception) {
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            return false;
        }
    }

    private Boolean processWithFallback(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            fallBackClient.create(savePaymentRequestDTO);
            paymentRepository.save(savePaymentRequestDTO, false);

            return true;
        } catch (PaymentProcessorException exception) {
            PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(false);
            return false;
        }
    }
}
