package com.jpdev01.rinha.service;

import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import com.jpdev01.rinha.state.ClientState;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentHealthCheckService {

    private final DefaultClient defaultClient;
    private final DefaultClient fallBackClient;
    private final DefaultClientState defaultClientState;
    private final FallbackClientState fallbackClientState;

    public PaymentHealthCheckService(DefaultClient defaultClient, DefaultClient fallBackClient, DefaultClientState defaultClientState, FallbackClientState fallbackClientState) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;

        final int period = 1;
        final int initialDelay = 0;

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(this::checkDefaultHealth, initialDelay, period, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkFallbackHealth, initialDelay, period, TimeUnit.SECONDS);
    }

    private void checkDefaultHealth() {
        try {
            if (defaultClientState.health()) return;
            if (!validateRateLimit(defaultClientState)) return;

            defaultClientState.setLastHealthCheckRun(System.currentTimeMillis());

            HealthResponseDTO healthResponseDTO = defaultClient.health().getBody();
            boolean isHealthy = isHealthy(healthResponseDTO);
            defaultClientState.setHealthy(isHealthy);
            if (isHealthy) {
                defaultClientState.setMinResponseTime(healthResponseDTO.minResponseTime());
            }
        } catch (Exception e) {
            defaultClientState.setHealthy(false);
        }
    }

    private void checkFallbackHealth() {
        try {
            if (fallbackClientState.health()) return;
            if (!validateRateLimit(fallbackClientState)) return;

            fallbackClientState.setLastHealthCheckRun(System.currentTimeMillis());

            HealthResponseDTO healthResponseDTO = fallBackClient.health().getBody();
            boolean isHealthy = isHealthy(healthResponseDTO);
            fallbackClientState.setHealthy(isHealthy);
            if (isHealthy) {
                fallbackClientState.setMinResponseTime(healthResponseDTO.minResponseTime());
            }
        } catch (Exception e) {
            fallbackClientState.setHealthy(false);
        }
    }

    private boolean isHealthy(HealthResponseDTO healthResponseDTO) {
        if (healthResponseDTO == null) return false;
        if (healthResponseDTO.failing()) return false;

        return true;
    }

    private boolean validateRateLimit(ClientState clientState) {
        int minimumInterval = 5000;

        return System.currentTimeMillis() - clientState.lastHealthCheckRun() > minimumInterval;
    }
}
