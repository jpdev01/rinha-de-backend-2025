package com.jpdev01.rinha.service;

import java.util.concurrent.atomic.AtomicBoolean;

public class PaymentProcessorHealthStatus {

    private AtomicBoolean defaultProcessorHealthy;
    private AtomicBoolean fallbackProcessorHealthy;

    private static final class InstanceHolder {
        private static final PaymentProcessorHealthStatus instance = new PaymentProcessorHealthStatus(true, true);
    }

    public static PaymentProcessorHealthStatus getInstance() {
        return InstanceHolder.instance;
    }

    public PaymentProcessorHealthStatus(boolean defaultProcessorHealthy, boolean fallbackProcessorHealthy) {
        this.defaultProcessorHealthy = new AtomicBoolean(defaultProcessorHealthy);
        this.fallbackProcessorHealthy = new AtomicBoolean(fallbackProcessorHealthy);
    }

    public void reset() {
        this.defaultProcessorHealthy = new AtomicBoolean(true);
        this.fallbackProcessorHealthy = new AtomicBoolean(true);
    }

    public boolean isDefaultProcessorHealthy() {
        return defaultProcessorHealthy.get();
    }

    public void setDefaultProcessorHealthy(boolean defaultProcessorHealthy) {
        this.defaultProcessorHealthy.set(defaultProcessorHealthy);
    }

    public boolean isFallbackProcessorHealthy() {
        return fallbackProcessorHealthy.get();
    }

    public void setFallbackProcessorHealthy(boolean fallbackProcessorHealthy) {
        this.fallbackProcessorHealthy.set(fallbackProcessorHealthy);
    }
}
