package com.jpdev01.rinha.repository;

import com.jpdev01.rinha.dto.PaymentProcessorSummaryDTO;
import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.entity.PaymentEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentSummaryResponseDTO summary(Instant from, Instant to) {
        String sql = """
            SELECT
                COUNT(*) AS total,
                SUM(amount) AS total_amount,
                processed_at_default
            FROM payments
            WHERE requested_at BETWEEN ? AND ?
            GROUP BY processed_at_default
            """;

        List<PaymentProcessorSummaryDTO> list = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PaymentProcessorSummaryDTO(
                        rs.getInt("total"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBoolean("processed_at_default")
                ),
                Timestamp.from(from),
                Timestamp.from(to)
        );

        // valores padrão se não houver registros
        PaymentProcessorSummaryDTO defaultSummary = new PaymentProcessorSummaryDTO(0, BigDecimal.ZERO, true);
        PaymentProcessorSummaryDTO fallbackSummary = new PaymentProcessorSummaryDTO(0, BigDecimal.ZERO, false);

        for (PaymentProcessorSummaryDTO dto : list) {
            if (Boolean.TRUE.equals(dto.processedAtDefault())) {
                defaultSummary = dto;
            } else {
                fallbackSummary = dto;
            }
        }

        return new PaymentSummaryResponseDTO(defaultSummary, fallbackSummary);
    }

    public void insertBatch(List<PaymentEntity> entities) {
        String sql = """
            INSERT INTO payments (correlation_id, amount, requested_at, processed_at_default)
            VALUES (?, ?, ?, ?)
            """;

        long start = System.nanoTime();
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PaymentEntity entity = entities.get(i);
                ps.setObject(1, entity.getCorrelationId());
                ps.setBigDecimal(2, entity.getAmount());
                ps.setTimestamp(3, Timestamp.from(entity.getRequestedAt()));
                ps.setBoolean(4, entity.isProcessedAtDefault());
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });

        long duration = System.nanoTime() - start;
        if (duration > 2_000_000) {
            System.err.println("Batch insert took too long: " + duration / 1_000_000 + " ms");
        }
    }

    public void insert(PaymentEntity entity) {
        String sql = """
            INSERT INTO payments (correlation_id, amount, requested_at, processed_at_default)
            VALUES (?, ?, ?, ?)
            """;

        long start = System.nanoTime();
        jdbcTemplate.update(sql,
                entity.getCorrelationId(),
                entity.getAmount(),
                Timestamp.from(entity.getRequestedAt()),
                entity.isProcessedAtDefault()
        );
        long duration = System.nanoTime() - start;
        if (duration > 1_000_000) {
            System.err.println("Insert took too long: " + duration / 1_000_000 + " ms");
        }
    }

    public void deleteAll() {
        String sql = "DELETE FROM payments";
        jdbcTemplate.update(sql);
    }
}