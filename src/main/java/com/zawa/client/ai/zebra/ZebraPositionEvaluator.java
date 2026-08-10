package com.zawa.client.ai.zebra;

public interface ZebraPositionEvaluator {
    int evaluate(long player, long opponent, long playerMoves);

    default boolean requiresLegalMoves() {
        return true;
    }
}
