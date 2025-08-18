package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.state.DefaultClientState;
import com.jpdev01.rinha.state.FallbackClientState;
import org.springframework.stereotype.Service;

@Service
public class PaymentAsyncService {

    private final PaymentService paymentService;
    private final DefaultClientState defaultClientState;

    private final static int PARALLELISM = 9;
    private final static int MAX_RETRIES = 15;
    private final FallbackClientState fallbackClientState;


    public PaymentAsyncService(DefaultClientState defaultClientState, PaymentService paymentService, FallbackClientState fallbackClientState) {
        this.defaultClientState = defaultClientState;
        this.fallbackClientState = fallbackClientState;

        this.paymentService = paymentService;

        for (int i = 0; i < PARALLELISM; i++) {
            Thread.startVirtualThread(this::runWorker);
        }
        for (int i = 0; i < 5; i++) {
            Thread.startVirtualThread(this::runDefaultWorker);
        }

        for (int i = 0; i < 1; i++) {
            Thread.startVirtualThread(this::runFallbackWorker);
        }
    }

    private void runWorker() {
        while (true) {
            var payment = getPayment();
            if (defaultClientState.health()) {
                if (processDefaultWithRetry(payment)) continue;

                PaymentQueue.getInstance().addToDefaultRetry(payment);
                continue;
            }

            if (fallbackClientState.health()) {
                if (processFallbackWithRetry(payment)) continue;

                PaymentQueue.getInstance().addToFallbackRetry(payment);
                continue;
            }

            PaymentQueue.getInstance().addToDefaultRetry(payment);
        }
    }

    public SavePaymentRequestDTO getDefaultPayment() {
        try {
            return PaymentQueue.getInstance().getDefaultQueue().take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public SavePaymentRequestDTO getFallbackPayment() {
        try {
            return PaymentQueue.getInstance().getFallbackQueue().take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void runDefaultWorker() {
        while (true) {
            var payment = getDefaultPayment();
            if (processDefaultWithRetry(payment)) continue;
            PaymentQueue.getInstance().addToDefaultRetry(payment);
        }
    }

    public void runFallbackWorker() {
        while (true) {
            var payment = getFallbackPayment();
            if (processFallbackWithRetry(payment)) continue;
            PaymentQueue.getInstance().addToFallbackRetry(payment);
        }
    }

    private boolean processDefaultWithRetry(SavePaymentRequestDTO payment) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                boolean success = paymentService.processDefault(payment);
                if (success) return true;
            } catch (Exception e) {
                System.err.println("Erro ao processar pagamento com o cliente padrão: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean processFallbackWithRetry(SavePaymentRequestDTO payment) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                boolean success = paymentService.processFallback(payment);
                if (success) return true;
            } catch (Exception e) {
                System.err.println("Erro ao processar pagamento com o cliente padrão: " + e.getMessage());
            }
        }
        return false;
    }

    public SavePaymentRequestDTO getPayment() {
        try {
            return PaymentQueue.getInstance().getQueue().take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
