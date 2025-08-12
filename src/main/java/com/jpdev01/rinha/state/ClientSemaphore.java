package com.jpdev01.rinha.state;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class ClientSemaphore {

    private long lastCheckedDefault;
    private long lastCheckedFallback;
    Semaphore defaultSemaphore;
    Semaphore FallbackSemaphore;

    public ClientSemaphore() {
        this.defaultSemaphore = new Semaphore(1);
        this.FallbackSemaphore = new Semaphore(1);
        this.lastCheckedDefault = 0;
        this.lastCheckedFallback = 0;
    }

    public boolean tryAcquireDefault() {
        try {
            return false;
//            if (!validate(lastCheckedDefault)) {
//                return false; // Rate limit exceeded
//            }
//            boolean acquired = defaultSemaphore.tryAcquire();
//            if (acquired) {
//                lastCheckedDefault = System.currentTimeMillis();
//            }
//            return acquired;
        } catch (Exception e) {
            return false;
        }
    }

    public void releaseDefault() {
        defaultSemaphore.release();
    }

    public boolean tryAcquireFallback() {
        try {
            return false;
//            if (!validate(lastCheckedFallback)) {
//                return false; // Rate limit exceeded
//            }
//            boolean acquired = FallbackSemaphore.tryAcquire();
//            if (acquired) {
//                lastCheckedDefault = System.currentTimeMillis();
//            }
//            return acquired;
        } catch (Exception e) {
            return false;
        }
    }

    public void releaseFallback() {
        FallbackSemaphore.release();
    }

    private boolean validate(long lastChecked) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastChecked) >= 1000; // 1 second
    }
}
