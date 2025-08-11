package com.jpdev01.rinha.service;

import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.client.PaymentClient;
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

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::checkDefaultHealth, initialDelay, period, TimeUnit.SECONDS);
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::checkFallbackHealth, initialDelay, period, TimeUnit.SECONDS);
    }

    private void checkDefaultHealth() {
        checkHealth(defaultClientState, defaultClient);
    }

    private void checkFallbackHealth() {
        checkHealth(fallbackClientState, fallBackClient);
    }

    private void checkHealth(ClientState state, PaymentClient client) {
        try {
            if (state.health()) return;
            if (!validateRateLimit(state)) return;

            state.setLastHealthCheckRun(System.currentTimeMillis());

            HealthResponseDTO healthResponseDTO = client.health().getBody();
            boolean isHealthy = isHealthy(healthResponseDTO);
            state.setHealthy(isHealthy);
            if (isHealthy) {
                state.setMinResponseTime(healthResponseDTO.minResponseTime());
            }
        } catch (Exception e) {
            state.setHealthy(false);
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
