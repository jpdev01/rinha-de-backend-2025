package com.jpdev01.rinha.service;

import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentHealthCheckService {

    private final DefaultClient defaultClient;
    private final DefaultClient fallBackClient;

    public PaymentHealthCheckService(DefaultClient defaultClient, DefaultClient fallBackClient) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;

        final int period = 5;
        final int initialDelay = 1;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkDefaultHealth, initialDelay, period, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkFallbackHealth, initialDelay, period, TimeUnit.SECONDS);
    }

    private void checkDefaultHealth() {
        try {
            if (PaymentProcessorState.getInstance().isDefaultProcessorHealthy()) return;
            HealthResponseDTO healthResponseDTO = defaultClient.health().getBody();
            PaymentProcessorState.getInstance().setDefaultProcessorHealthy(isHealthy(healthResponseDTO));
        } catch (Exception e) {
            PaymentProcessorState.getInstance().setDefaultProcessorHealthy(false);
        }
    }

    private void checkFallbackHealth() {
        try {
            if (PaymentProcessorState.getInstance().isFallbackProcessorHealthy()) return;
            HealthResponseDTO healthResponseDTO = fallBackClient.health().getBody();
            PaymentProcessorState.getInstance().setFallbackProcessorHealthy(isHealthy(healthResponseDTO));
        } catch (Exception e) {
            PaymentProcessorState.getInstance().setFallbackProcessorHealthy(false);
        }
    }

    private boolean isHealthy(HealthResponseDTO healthResponseDTO) {
        if (healthResponseDTO == null) return false;
        if (healthResponseDTO.failing()) return false;
        if (healthResponseDTO.minResponseTime() > 0) return false;

        return true;
    }
}
