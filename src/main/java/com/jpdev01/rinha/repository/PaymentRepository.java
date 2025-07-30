package com.jpdev01.rinha.repository;

import com.jpdev01.rinha.dto.PaymentProcessorSummaryDTO;
import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.jooq.tables.records.PaymentsRecord;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jpdev01.rinha.jooq.Tables.PAYMENTS;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.sum;

@Repository
public class PaymentRepository {

    private final DSLContext dslContext;

    public PaymentRepository(DSLContext dslContext) {
        this.dslContext = dslContext;

    }

    public void save(SavePaymentRequestDTO paymentRequestDto, Boolean byDefault) {
        PaymentsRecord paymentsRecord = dslContext.newRecord(PAYMENTS);
        paymentsRecord.setCorrelationId(UUID.randomUUID());
        dslContext.insertInto(PAYMENTS)
                .set(PAYMENTS.CORRELATION_ID, UUID.randomUUID())
                .set(PAYMENTS.AMOUNT, paymentRequestDto.amount())
                .set(PAYMENTS.REQUESTED_AT, LocalDateTime.now())
                .set(PAYMENTS.PROCESSED_AT_DEFAULT, byDefault)
                .execute();
    }

    public void purgeAll() {
        dslContext.deleteFrom(PAYMENTS)
                .execute();
    }

    public PaymentSummaryResponseDTO summary(LocalDateTime from, LocalDateTime to) {
        List<PaymentProcessorSummaryDTO> elements = dslContext.select(
                        count().as("totalRequests"),
                        sum(PAYMENTS.AMOUNT).as("totalAmount"),
                        PAYMENTS.PROCESSED_AT_DEFAULT.as("processedAtDefault")
                )
                .from(PAYMENTS)
                .where(PAYMENTS.REQUESTED_AT.between(from, to))
                .groupBy(PAYMENTS.PROCESSED_AT_DEFAULT)
                .fetchInto(PaymentProcessorSummaryDTO.class);

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
