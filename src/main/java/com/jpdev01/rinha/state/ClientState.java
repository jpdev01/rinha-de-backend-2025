package com.jpdev01.rinha.state;


public interface ClientState {

    boolean health();
    void setHealthy(boolean healthy);

    int lastHealthCheckRun();
    void setLastHealthCheckRun(int lastHealthCheckRun);

}
