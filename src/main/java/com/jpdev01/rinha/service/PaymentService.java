package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.PaymentSummaryResponseDTO;
import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.exception.PaymentProcessorException;
import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;

@Service
public class PaymentService {

    private final DefaultClient defaultClient;
    private final PaymentRepository paymentRepository;

    public PaymentService(DefaultClient defaultClient, PaymentRepository paymentRepository) {
        this.defaultClient = defaultClient;
        this.paymentRepository = paymentRepository;
    }

    public void process(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            defaultClient.process(savePaymentRequestDTO);
            paymentRepository.save(savePaymentRequestDTO, true);
        } catch (PaymentProcessorException exception) {
        }
    }

    public void purge() {
        paymentRepository.purgeAll();
    }

    public PaymentSummaryResponseDTO getPayments(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Os parâmetros 'from' e 'to' não podem ser nulos.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' deve ser anterior a 'to'.");
        }
        return paymentRepository.summary(from, to);
    }
}
