package com.jpdev01.rinha.service;

public class PaymentProcessorHealthStatus {

    private boolean defaultProcessorHealthy;
    private boolean fallbackProcessorHealthy;

    private static final class InstanceHolder {
        private static final PaymentProcessorHealthStatus instance = new PaymentProcessorHealthStatus(true, true);
    }

    public static PaymentProcessorHealthStatus getInstance() {
        return InstanceHolder.instance;
    }

    public PaymentProcessorHealthStatus(boolean defaultProcessorHealthy, boolean fallbackProcessorHealthy) {
        this.defaultProcessorHealthy = defaultProcessorHealthy;
        this.fallbackProcessorHealthy = fallbackProcessorHealthy;
    }

    public boolean isDefaultProcessorHealthy() {
        return defaultProcessorHealthy;
    }

    public void setDefaultProcessorHealthy(boolean defaultProcessorHealthy) {
        this.defaultProcessorHealthy = defaultProcessorHealthy;
    }

    public boolean isFallbackProcessorHealthy() {
        return fallbackProcessorHealthy;
    }

    public void setFallbackProcessorHealthy(boolean fallbackProcessorHealthy) {
        this.fallbackProcessorHealthy = fallbackProcessorHealthy;
    }
}
