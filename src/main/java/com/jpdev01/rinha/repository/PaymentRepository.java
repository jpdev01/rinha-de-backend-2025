package com.jpdev01.rinha.repository;

import com.jpdev01.rinha.dto.PaymentProcessorSummaryDTO;
import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Repository
public class PaymentRepository {

    private final DataSource dataSource;

    public PaymentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(SavePaymentRequestDTO paymentRequestDto, Boolean byDefault) {
        String sql = """
            INSERT INTO payments (
                correlation_id,
                amount,
                requested_at,
                processed_at_default
            ) VALUES (?, ?, ?, ?)
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, paymentRequestDto.correlationIdAsUUID());
            stmt.setBigDecimal(2, paymentRequestDto.amount());
            stmt.setTimestamp(3, java.sql.Timestamp.valueOf(paymentRequestDto.requestedAt()));
            stmt.setBoolean(4, byDefault);

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar pagamento", e);
        }
    }

    public void purgeAll() {
        String sql = "DELETE FROM payments";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao purgar pagamentos", e);
        }
    }

    public PaymentSummaryResponseDTO summary(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                COUNT(*) AS total,
                SUM(amount) AS total_amount,
                processed_at_default
            FROM payments
            WHERE requested_at BETWEEN ? AND ?
            GROUP BY processed_at_default
                """;
        List<PaymentProcessorSummaryDTO> elements;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, java.sql.Timestamp.valueOf(from));
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(to));

            var resultSet = stmt.executeQuery();
            elements = new java.util.ArrayList<>();

            while (resultSet.next()) {
                int total = resultSet.getInt("total");
                BigDecimal totalAmount = resultSet.getBigDecimal("total_amount");
                boolean processedAtDefault = resultSet.getBoolean("processed_at_default");

                elements.add(new PaymentProcessorSummaryDTO(total, totalAmount, processedAtDefault));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao obter resumo de pagamentos", e);
        }

        if (elements.isEmpty() || elements.get(0).processedAtDefault() == true) {
            return new PaymentSummaryResponseDTO(resolveNullable(elements, 0), resolveNullable(elements, 1));
        } else {
            return new PaymentSummaryResponseDTO(resolveNullable(elements, 1), resolveNullable(elements, 0));
        }
    }

    private PaymentProcessorSummaryDTO resolveNullable(List<PaymentProcessorSummaryDTO> elements, Integer index) {
        if (elements.size() > index) {
            return elements.get(index);
        } else {
            return new PaymentProcessorSummaryDTO(0, BigDecimal.ZERO, false);
        }
    }
}
