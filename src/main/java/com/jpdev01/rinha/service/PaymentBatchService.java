package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.exception.PaymentProcessorException;
import com.jpdev01.rinha.integration.client.DefaultClient;
import com.jpdev01.rinha.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.jpdev01.rinha.Utils.isDelayed;

@Transactional
@Service
public class PaymentBatchService {

    private final PaymentRepository paymentRepository;
    private final DefaultClient defaultClient;

    public PaymentBatchService(PaymentRepository paymentRepository, DefaultClient defaultClient) {
        this.paymentRepository = paymentRepository;
        this.defaultClient = defaultClient;
    }

    @Transactional
    public void processBatchWithDefault(List<SavePaymentRequestDTO> batch) {
        System.out.println("Processing batch with default client, size: " + batch.size());
        for (SavePaymentRequestDTO payment : batch) {
            boolean processed = false;
            boolean isDelayed = false;
            try {
                long start = System.nanoTime();
                boolean success = defaultClient.create(payment);
                if (success) {
                    processed = true;
                    paymentRepository.save(payment, true);
                } else {
                    processed = false;
                }
                if (isDelayed(start, System.nanoTime())) {
                    PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
                    isDelayed = true;
                }
            } catch (PaymentProcessorException exception) {
                PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
                processed = false;
            } catch (Exception exception) {
                System.err.println("Error processing payment with default client: " + exception.getMessage());
                processed = false;
            }
            if (processed) {
                System.out.println("Payment processed successfully with default client: " + payment.correlationIdAsUUID());
                try {
                    batch.remove(payment);
                } catch (Exception e) {
                    System.err.println("Error removing payment from batch: " + e.getMessage());
                }
            }
            if (isDelayed || !processed) break;
            continue;
        }
        PaymentQueue.getInstance().getQueue().addAll(batch);
    }

    private boolean processWithDefault(SavePaymentRequestDTO savePaymentRequestDTO) {
        try {
            long start = System.nanoTime();
            boolean success = defaultClient.create(savePaymentRequestDTO);
            if (!success) return false;
            if (isDelayed(start, System.nanoTime())) {
                PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            }
            paymentRepository.save(savePaymentRequestDTO, true);

            return true;
        } catch (PaymentProcessorException exception) {
            PaymentProcessorHealthStatus.getInstance().setDefaultProcessorHealthy(false);
            return false;
        }
    }
}
