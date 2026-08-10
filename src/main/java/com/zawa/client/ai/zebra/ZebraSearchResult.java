package com.zawa.client.ai.zebra;

public record ZebraSearchResult(int move, int score, int completedDepth, long nodes, long elapsedMilliseconds,
                                boolean exact, boolean outcomeSolved) {
}
