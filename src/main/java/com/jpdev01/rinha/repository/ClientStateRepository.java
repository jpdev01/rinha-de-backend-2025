package com.jpdev01.rinha.repository;

import com.jpdev01.rinha.state.ClientState;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class ClientStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClientStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateClientState(ClientState client) {
        String sql;
        if (client instanceof DefaultClientState) {
            sql = "UPDATE payment_processors_state SET default_healthy = ?, default_min_response_time_ms = ?, default_last_checked = ?";
        } else {
            sql = "UPDATE payment_processors_state SET fallback_healthy = ?, fallback_min_response_time_ms = ?, fallback_last_checked = ?";
        }

        int affectedRows = jdbcTemplate.update(sql, ps -> {
            ps.setBoolean(1, client.health());
            ps.setInt(2, client.getMinResponseTime());
            ps.setLong(3, client.lastHealthCheckRun());
        });
        if (affectedRows == 0) {
            jdbcTemplate.update("INSERT INTO payment_processors_state (default_healthy, default_min_response_time_ms, default_last_checked, fallback_healthy, fallback_min_response_time_ms, fallback_last_checked) VALUES (?, ?, ?, ?, ?, ?)",
                    client.health(), client.getMinResponseTime(), client.lastHealthCheckRun(),
                    client.health(), client.getMinResponseTime(), client.lastHealthCheckRun());
        }
    }

    public void insertIfNecessary(DefaultClientState defaultClientState, FallbackClientState fallbackClientState) {
        String sql = """
                INSERT INTO payment_processors_state (
                    default_healthy, default_min_response_time_ms, default_last_checked,
                    fallback_healthy, fallback_min_response_time_ms, fallback_last_checked
                )
                SELECT ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM payment_processors_state
                )
                """;
        jdbcTemplate.update(sql,
                defaultClientState.health(), defaultClientState.getMinResponseTime(), defaultClientState.lastHealthCheckRun(),
                fallbackClientState.health(), fallbackClientState.getMinResponseTime(), fallbackClientState.lastHealthCheckRun());
    }

    public Map get(ClientState clientState) {
        if (clientState instanceof DefaultClientState) {
            String sql = "SELECT default_healthy AS healthy, default_min_response_time_ms AS min_response_time, default_last_checked AS last_checked FROM payment_processors_state WHERE default_last_checked > ?";
            return executeGet(sql, clientState.lastHealthCheckRun());
        } else if (clientState instanceof FallbackClientState) {
            String sql = "SELECT fallback_healthy AS healthy, fallback_min_response_time_ms AS min_response_time, fallback_last_checked AS last_checked FROM payment_processors_state WHERE fallback_last_checked > ?";
            return executeGet(sql, clientState.lastHealthCheckRun());
        } else {
            throw new IllegalArgumentException("Unknown client state type: " + clientState.getClass().getName());
        }
    }

    private Map executeGet(String sql, long lastChecked) {
        return jdbcTemplate.query(sql, ps -> {
            ps.setLong(1, lastChecked);
        }, rs -> {
            if (rs.next()) {
                return Map.of(
                        "healthy", rs.getBoolean("healthy"),
                        "min_response_time", rs.getInt("min_response_time"),
                        "last_checked", rs.getLong("last_checked")
                );
            }
            return Map.of();
        });
    }
}
