package com.jpdev01.rinha.state;


public interface ClientState {

    boolean health();
    void setHealthy(boolean healthy);

    long lastHealthCheckRun();
    void setLastHealthCheckRun(long lastHealthCheckRun);

    int getMinResponseTime();
    void setMinResponseTime(int minResponseTime);

    default boolean isMinimumResponseTimeUnder(long expectedResponseTime) {
        return getMinResponseTime() <= expectedResponseTime;
    }
}
