package com.jpdev01.rinha.service;

import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.integration.client.FallbackClient;
import com.jpdev01.rinha.integration.client.PaymentClient;
import com.jpdev01.rinha.integration.dto.HealthResponseDTO;
import com.jpdev01.rinha.state.ClientState;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentHealthCheckService {

    private final DefaultClient defaultClient;
    private final FallbackClient fallBackClient;
    private final DefaultClientState defaultClientState;
    private final FallbackClientState fallbackClientState;

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Value("${services.execute-health-check}")
    private boolean executeHealthCheck;

    public PaymentHealthCheckService(DefaultClient defaultClient, FallbackClient fallBackClient, DefaultClientState defaultClientState, FallbackClientState fallbackClientState, R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.defaultClient = defaultClient;
        this.fallBackClient = fallBackClient;
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;

        try {
            insertPaymentProcessorState();
        } catch (Exception e) {
            System.err.println("Error initializing payment processor state: " + e.getMessage());
        }

        final int period = 2;
        final int initialDelay = 0;

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::checkDefaultHealth, initialDelay, period, TimeUnit.SECONDS);
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::checkFallbackHealth, initialDelay, period, TimeUnit.SECONDS);
    }

    private void checkDefaultHealth() {
        if (executeHealthCheck) {
            checkHealth(defaultClientState, defaultClient);
        } else {
            checkHealthDatabaseBased(defaultClientState, "default");
        }
    }

    private void checkFallbackHealth() {
        try {
            if (executeHealthCheck) {
                checkHealth(fallbackClientState, fallBackClient);
            } else {
                checkHealthDatabaseBased(fallbackClientState, "fallback");
            }
        } catch (Exception exception) {
            System.err.println("Error checking fallback health: " + exception.getMessage());
        }
    }

    private void checkHealthDatabaseBased(ClientState state, String clientName) {
        try {
            String healthyColumn = clientName + "_healthy";
            String minResponseTimeColumn = clientName + "_min_response_time_ms";
            String lastCheckedColumn = clientName + "_last_checked";

            String sql = String.format(
                    "SELECT %s AS healthy, %s AS minimum_response_time, %s as last_checked FROM payment_processors_state WHERE %s > %d",
                    healthyColumn, minResponseTimeColumn, lastCheckedColumn, lastCheckedColumn, state.lastHealthCheckRun()
            );

            this.r2dbcEntityTemplate
                    .getDatabaseClient()
                    .sql(sql)
                    .fetch()
                    .first()
                    .switchIfEmpty(Mono.defer(() -> {
                        return Mono.empty(); // ou Mono.just(state) se quiser emitir algo
                    }))
                    .flatMap(row -> {
                        if (row == null) {
                            return Mono.empty();
                        }
                        boolean isHealthy = (Boolean) row.get("healthy");
                        int minResponseTime = (Integer) row.get("minimum_response_time");
                        long lastChecked = (Long) row.get("last_checked");
                        state.setHealthy(isHealthy);
                        state.setMinResponseTime(minResponseTime);
                        state.setLastHealthCheckRun(lastChecked);
                        return Mono.empty();
                    })
                    .doOnError(error -> {
                        System.err.println("Error checking health for " + clientName + " client from database: " + error.getMessage());
                    })
                    .subscribe(
                            success -> System.out.println("Health check completed for " + clientName + " client from database."),
                            error -> System.err.println("Error during health check for " + clientName + " client from database: " + error.getMessage())
                    );
        } catch (Throwable e) {
            System.err.println("Error during health check for " + clientName + " client from database: " + e.getMessage());
        }
    }

    private void checkHealth(final ClientState state, final PaymentClient client) {
        try {
            if (state.health() && state.isMinimumResponseTimeUnder(10)) return;
            if (!validateRateLimit(state)) return;

            state.setLastHealthCheckRun(System.currentTimeMillis());

            client.health()
                    .flatMap(response -> {
                        if (response != null) {
                            System.out.println("health responsed");
                            state.setHealthy(!response.failing());
                            state.setMinResponseTime(response.minResponseTime());
                            updateDb(state, !response.failing(), response.minResponseTime());
                        }
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (Exception e) {
            System.err.println("Error during health check for " + client.getClass().getSimpleName() + ": " + e.getMessage());
            state.setHealthy(false);
        }
    }

    private boolean validateRateLimit(ClientState clientState) {
        int minimumInterval = 5000;

        return System.currentTimeMillis() - clientState.lastHealthCheckRun() > minimumInterval;
    }

    private static final String CHECK_TABLE_SQL =
            "SELECT COUNT(*) as cnt FROM payment_processors_state";

    private static final String INSERT_SQL =
            "INSERT INTO payment_processors_state " +
                    "(default_healthy, default_min_response_time_ms, fallback_healthy, fallback_min_response_time_ms, default_last_checked, fallback_last_checked) " +
                    "VALUES (false, 0, false, 0, 0, 0)";

    private void insertPaymentProcessorState() {
        this.r2dbcEntityTemplate.getDatabaseClient()
                .sql(CHECK_TABLE_SQL)
                .map((row, meta) -> ((Number) row.get("cnt")).longValue())
                .first()
                .filter(count -> count == 0)
                .flatMap(ignore ->
                        this.r2dbcEntityTemplate.getDatabaseClient()
                                .sql(INSERT_SQL)
                                .fetch()
                                .rowsUpdated()
                )
                .doOnSuccess(rows -> {
                    if (rows != null && rows > 0) {
                        System.out.println("Tabela 'payment_processors_state' inicializada com registro padrão.");
                    }
                })
                .doOnError(error -> System.err.println("Erro ao inicializar tabela 'payment_processors_state'" + error.getMessage()))
                .subscribe();
    }

    private void updateDb(ClientState client, boolean isHealthy, int minResponseTime) {
        String sql;
        if (client instanceof DefaultClientState) {
            sql = "UPDATE payment_processors_state SET default_healthy = :healthy, default_min_response_time_ms = :minResponseTime, default_last_checked = :lastChecked";
        } else {
            System.out.println("Updating fallback client state in database: healthy=" + isHealthy + ", minResponseTime=" + minResponseTime);
            sql = "UPDATE payment_processors_state SET fallback_healthy = :healthy, fallback_min_response_time_ms = :minResponseTime, fallback_last_checked = :lastChecked";
        }
        this.r2dbcEntityTemplate
                .getDatabaseClient()
                .sql(sql)
                .bind("healthy", isHealthy)
                .bind("minResponseTime", minResponseTime)
                .bind("lastChecked", System.currentTimeMillis())
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> {
                    if (rows <= 0) {
                        System.out.println("No rows updated in payment_processors_state.");
                    }
                    return Mono.empty();
                })
                .subscribe();
    }
}
