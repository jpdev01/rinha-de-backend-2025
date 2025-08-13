package com.jpdev01.rinha.state;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class ClientRecoverSemaphore {

    private long lastCheckedDefault;
    private long lastCheckedFallback;
    Semaphore defaultSemaphore;
    Semaphore fallbackSemaphore;

    public ClientRecoverSemaphore() {
        this.defaultSemaphore = new Semaphore(1);
        this.fallbackSemaphore = new Semaphore(1);
        this.lastCheckedDefault = 0;
        this.lastCheckedFallback = 0;
    }

    public boolean tryAcquireDefault() {
        try {
            if (!validate(lastCheckedDefault)) return false;

            boolean acquired = defaultSemaphore.tryAcquire(2, TimeUnit.MILLISECONDS);
            if (acquired) {
                lastCheckedDefault = System.currentTimeMillis();
            }
            return acquired;
        } catch (Exception e) {
            return false;
        }
    }

    public void releaseDefault() {
        defaultSemaphore.release();
    }

    public boolean tryAcquireFallback() {
        try {
            if (!validate(lastCheckedFallback)) return false;

            boolean acquired = fallbackSemaphore.tryAcquire(2, TimeUnit.MILLISECONDS);
            if (acquired) {
                lastCheckedFallback = System.currentTimeMillis();
            }
            return acquired;
        } catch (Exception e) {
            return false;
        }
    }

    public void releaseFallback() {
        fallbackSemaphore.release();
    }

    private boolean validate(long lastChecked) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastChecked) >= 1000; // 1 second
    }
}
