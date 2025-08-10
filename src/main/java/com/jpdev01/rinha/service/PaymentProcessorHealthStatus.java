package com.jpdev01.rinha.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PaymentProcessorHealthStatus {

    private AtomicBoolean defaultProcessorHealthy;
    private AtomicBoolean fallbackProcessorHealthy;

    private AtomicInteger defaultProcessorLastCheckTime = new AtomicInteger(0);
    private AtomicInteger fallbackProcessorLastCheckTime = new AtomicInteger(0);

    private AtomicBoolean defaultProcessorProbing = new AtomicBoolean(false);
    private AtomicBoolean fallbackProcessorProbing = new AtomicBoolean(false);

    private static final class InstanceHolder {
        private static final PaymentProcessorHealthStatus instance = new PaymentProcessorHealthStatus(false, false);
    }

    public static PaymentProcessorHealthStatus getInstance() {
        return InstanceHolder.instance;
    }

    public PaymentProcessorHealthStatus(boolean defaultProcessorHealthy, boolean fallbackProcessorHealthy) {
        this.defaultProcessorHealthy = new AtomicBoolean(defaultProcessorHealthy);
        this.fallbackProcessorHealthy = new AtomicBoolean(fallbackProcessorHealthy);
        this.defaultProcessorLastCheckTime.set(0);
        this.fallbackProcessorLastCheckTime.set(0);
    }

    public void reset() {
        this.defaultProcessorHealthy = new AtomicBoolean(false);
        this.fallbackProcessorHealthy = new AtomicBoolean(false);
        this.defaultProcessorLastCheckTime.set(0);
        this.fallbackProcessorLastCheckTime.set(0);
    }

    public boolean isDefaultProcessorHealthy() {
        return defaultProcessorHealthy.get();
    }

    public void setDefaultProcessorHealthy(boolean defaultProcessorHealthy) {
        this.defaultProcessorHealthy.set(defaultProcessorHealthy);
        this.defaultProcessorLastCheckTime.set((int) System.currentTimeMillis());
    }

    public boolean isFallbackProcessorHealthy() {
        return fallbackProcessorHealthy.get();
    }

    public void setFallbackProcessorHealthy(boolean fallbackProcessorHealthy) {
        this.fallbackProcessorHealthy.set(fallbackProcessorHealthy);
        this.fallbackProcessorLastCheckTime.set((int) System.currentTimeMillis());
    }

    public boolean shouldCheckDefaultProcessor() {
        if (this.isDefaultProcessorHealthy()) return false;
        return System.currentTimeMillis() - defaultProcessorLastCheckTime.get() > 1;
    }

    public boolean shouldCheckFallbackProcessor() {
        if (this.isFallbackProcessorHealthy()) return false;
        return System.currentTimeMillis() - fallbackProcessorLastCheckTime.get() > 1;
    }

    public boolean isDefaultProcessorProbing() {
        return defaultProcessorProbing.get();
    }

    public void setDefaultProcessorProbing(boolean probing) {
        this.defaultProcessorProbing.set(probing);
    }

    public boolean isFallbackProcessorProbing() {
        return fallbackProcessorProbing.get();
    }

    public void setFallbackProcessorProbing(boolean probing) {
        this.fallbackProcessorProbing.set(probing);
    }
}
