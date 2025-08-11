package com.jpdev01.rinha.state;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DefaultClientState implements ClientState {

    private AtomicBoolean healthy;
    private long lastHealthCheckRun;
    private int minResponseTime;

    public DefaultClientState() {
        this.healthy = new AtomicBoolean(false);
        this.lastHealthCheckRun = 0;
        this.minResponseTime = 0;
    }

    @Override
    public boolean health() {
        return healthy.get();
    }

    @Override
    public long lastHealthCheckRun() {
        return lastHealthCheckRun;
    }

    @Override
    public void setLastHealthCheckRun(long lastHealthCheckRun) {
        this.lastHealthCheckRun = lastHealthCheckRun;
    }

    @Override
    public void setHealthy(boolean healthy) {
        this.healthy.set(healthy);
    }

    @Override
    public int getMinResponseTime() {
        return minResponseTime;
    }

    @Override
    public void setMinResponseTime(int minResponseTime) {
        this.minResponseTime = minResponseTime;
    }
}
