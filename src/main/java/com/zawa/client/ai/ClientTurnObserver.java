package com.zawa.client.ai;

public interface ClientTurnObserver {
    void turnObserved(Integer[][] board, int myTurn, int currentTurn);

    void gameFinished();
}
