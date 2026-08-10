package com.zawa.client.util;

public class TimeManager {
    private long endTime;
    private int nodeCount;

    public void start(int timeLimitMilliseconds) {
        this.endTime = System.currentTimeMillis() + timeLimitMilliseconds;
        this.nodeCount = 0;
    }

    public void checkTime() {
        nodeCount++;
        if ((nodeCount & 4095) == 0) {
            if (System.currentTimeMillis() >= endTime) {
                throw new TimeOutException();
            }
        }
    }

    public static class TimeOutException extends RuntimeException {
    }
}