package com.jpdev01.rinha.service;

import com.jpdev01.rinha.dto.SavePaymentRequestDTO;
import com.jpdev01.rinha.entity.PaymentEntity;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PaymentQueue {

    BlockingQueue<SavePaymentRequestDTO> queue = new LinkedBlockingQueue<>();
    BlockingQueue<SavePaymentRequestDTO> defaultQueue = new LinkedBlockingQueue<>();
    BlockingQueue<SavePaymentRequestDTO> fallbackQueue = new LinkedBlockingQueue<>();
    BlockingQueue<PaymentEntity> insertQueue = new LinkedBlockingQueue<>();

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

    public BlockingQueue<SavePaymentRequestDTO> getQueue() {
        return queue;
    }

    public void addToDefaultRetry(SavePaymentRequestDTO payment) {
        defaultQueue.add(payment);
    }
    
    public void addToFallbackRetry(SavePaymentRequestDTO payment) {
        fallbackQueue.add(payment);
    }
    
    public BlockingQueue<SavePaymentRequestDTO> getFallbackQueue() {
        return fallbackQueue;
    }

    public BlockingQueue<SavePaymentRequestDTO> getDefaultQueue() {
        return defaultQueue;
    }

    public BlockingQueue<PaymentEntity> getInsertQueue() {
        return insertQueue;
    }

    public void addToInsertQueue(PaymentEntity payment) {
        insertQueue.add(payment);
    }
}
