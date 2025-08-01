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
import java.util.function.Consumer;

import static com.jpdev01.rinha.Utils.isDelayed;

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

    public void process(SavePaymentRequestDTO savePaymentRequestDTO) {
        if (PaymentProcessorHealthStatus.getInstance().isDefaultProcessorHealthy()) {
            if (!processWithDefault(savePaymentRequestDTO)) {
//                PaymentQueue.getInstance().addToDLQ(savePaymentRequestDTO);
                PaymentQueue.getInstance().add(savePaymentRequestDTO);
            }
        } else if (PaymentProcessorHealthStatus.getInstance().isFallbackProcessorHealthy()) {
            if (!processWithFallback(savePaymentRequestDTO)) {
                PaymentQueue.getInstance().add(savePaymentRequestDTO);
            }
        }
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

    private boolean processWithDefault(SavePaymentRequestDTO savePaymentRequestDTO) {
        long start = System.nanoTime();
        if (!defaultClient.create(savePaymentRequestDTO)) {
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            return false;
        }
        if (isDelayed(start, System.nanoTime())) {
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
        }
        paymentRepository.save(savePaymentRequestDTO, true);

        return true;
    }

    private boolean processWithFallback(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            long start = System.nanoTime();
            if (!fallBackClient.create(savePaymentRequestDTO)) {
                PaymentProcessorHealthStatus.getInstance().setFallbackProcessorHealthy(false);
                return false;
            }

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
