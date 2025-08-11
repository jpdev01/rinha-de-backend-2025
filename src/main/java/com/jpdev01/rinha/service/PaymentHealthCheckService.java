package com.jpdev01.rinha.service;

import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import com.jpdev01.rinha.state.ClientState;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
        final int initialDelay = 1;

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::checkDefaultHealth, initialDelay, period, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkFallbackHealth, initialDelay, period, TimeUnit.SECONDS);
    }

    private void checkDefaultHealth() {
        try {
            if (defaultClientState.health()) return;
            if (!validateRateLimit(defaultClientState)) return;

            System.out.println("Checking default client health...");
            defaultClient.health().subscribe(healthResponseEntity -> {
                System.out.println("Default client health check completed.");
                if (HttpStatus.TOO_MANY_REQUESTS.equals(healthResponseEntity.getStatusCode())) {
                    System.err.println("Default client health check rate limit exceeded.");
                    return;
                }
                HealthResponseDTO healthResponseDTO = healthResponseEntity.getBody();
                defaultClientState.setHealthy(isHealthy(healthResponseDTO));
            }, throwable -> {
                defaultClientState.setHealthy(false);
            });
        } catch (Exception e) {
        }
    }

    private void checkFallbackHealth() {
        try {
            if (fallbackClientState.health()) return;
            if (!validateRateLimit(fallbackClientState)) return;

            fallBackClient.health().subscribe(healthResponseEntity -> {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(healthResponseEntity.getStatusCode())) {
                    System.err.println("Fallback client health check rate limit exceeded.");
                    return;
                }
                HealthResponseDTO healthResponseDTO = healthResponseEntity.getBody();
                fallbackClientState.setHealthy(isHealthy(healthResponseDTO));
            }, throwable -> {
                fallbackClientState.setHealthy(false);
            });
        } catch (Exception e) {
            fallbackClientState.setHealthy(false);
        }
    }

    private boolean isHealthy(HealthResponseDTO healthResponseDTO) {
        if (healthResponseDTO == null) return false;
        if (healthResponseDTO.failing()) return false;
        if (healthResponseDTO.minResponseTime() > 0) return false;

        return true;
    }

    private boolean validateRateLimit(ClientState clientState) {
        int minimumInterval = 5000;

        return System.currentTimeMillis() - clientState.lastHealthCheckRun() > minimumInterval;
    }
}
