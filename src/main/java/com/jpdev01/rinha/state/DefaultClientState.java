package com.jpdev01.rinha.state;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DefaultClientState implements ClientState {

    private AtomicBoolean healthy;
    private int lastHealthCheckRun;
    private long lastFailure;

    private Semaphore retrySemaphore;

    public DefaultClientState() {
        this.healthy = new AtomicBoolean(false);
        this.lastHealthCheckRun = 0;
        this.retrySemaphore = new Semaphore(1, true);
    }

    @Override
    public boolean health() {
        return healthy.get();
    }

    @Override
    public int lastHealthCheckRun() {
        return lastHealthCheckRun;
    }

    @Override
    public void setLastHealthCheckRun(int lastHealthCheckRun) {
        this.lastHealthCheckRun = lastHealthCheckRun;
    }

    @Override
    public void setHealthy(boolean healthy) {
        if (!healthy) setLastFailure(System.currentTimeMillis());
        this.healthy.set(healthy);
    }

    @Override
    public long getLastFailure() {
        return lastFailure;
    }

    @Override
    public void setLastFailure(long lastFailure) {
        this.lastFailure = lastFailure;
    }

    @Override
    public boolean acquireRetry() {
        try {
            if (!canRetry()) return false;
            return retrySemaphore.tryAcquire();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canRetry() {
        int minimumRetryIntervalMs = 400;
        return (System.currentTimeMillis() - lastFailure) >= minimumRetryIntervalMs;
    }
}
