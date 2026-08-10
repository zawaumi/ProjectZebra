package com.zawa.client.ai.zebra;

final class ZebraSearchClock {
    private static final SearchTimeout TIMEOUT = new SearchTimeout();

    private long softDeadline;
    private long hardDeadline;
    private long nodes;
    private boolean unlimited;

    void start(long softMilliseconds, long hardMilliseconds) {
        long now = System.nanoTime();
        softDeadline = now + softMilliseconds * 1_000_000L;
        hardDeadline = now + hardMilliseconds * 1_000_000L;
        nodes = 0L;
        unlimited = false;
    }

    void startUnlimited() {
        softDeadline = Long.MAX_VALUE;
        hardDeadline = Long.MAX_VALUE;
        nodes = 0L;
        unlimited = true;
    }

    void visit() {
        nodes++;
        if (Thread.currentThread().isInterrupted()
                || (!unlimited && (nodes & 511L) == 0L && System.nanoTime() >= hardDeadline)) {
            throw TIMEOUT;
        }
    }

    void checkNow() {
        if (Thread.currentThread().isInterrupted() || (!unlimited && System.nanoTime() >= hardDeadline)) {
            throw TIMEOUT;
        }
    }

    boolean softExpired() {
        return !unlimited && System.nanoTime() >= softDeadline;
    }

    long nodes() {
        return nodes;
    }

    static final class SearchTimeout extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private SearchTimeout() {
            super(null, null, false, false);
        }
    }
}
