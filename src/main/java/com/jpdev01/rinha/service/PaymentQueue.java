package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PaymentQueue {

    Queue<SavePaymentRequestDTO> queue = new ConcurrentLinkedQueue<>();
    Queue<SavePaymentRequestDTO> dlq = new ConcurrentLinkedQueue<>();

    private static final class InstanceHolder {
        private static final PaymentQueue instance = new PaymentQueue();
    }

    public static PaymentQueue getInstance() {
        return InstanceHolder.instance;
    }

    public void add(SavePaymentRequestDTO payment) {
        queue.add(payment);
    }

    public SavePaymentRequestDTO poll() {
        return queue.poll();
    }

    public void addToDLQ(SavePaymentRequestDTO payment) {
        dlq.add(payment);
    }

    public SavePaymentRequestDTO pollDLQ() {
        return dlq.poll();
    }
}
