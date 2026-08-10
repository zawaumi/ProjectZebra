package com.zawa.client.ai.zebra;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZebraPonderEngineTest {
    @Test
    void ponderingReturnsACompletedReplyForTheObservedMove() {
        ZebraVanguardPatternModel model = ZebraVanguardPatternModel.loadDefault();
        ZebraPonderEngine ponder = new ZebraPonderEngine(model);
        long black = ZebraBitBoard.INITIAL_BLACK;
        long white = ZebraBitBoard.INITIAL_WHITE;
        ponder.start(black, white);
        LockSupport.parkNanos(150_000_000L);
        int move = 37;
        long flipped = ZebraBitBoard.flips(black, white, move);
        long player = white ^ flipped;
        long opponent = black | flipped | (1L << move);
        ZebraSearchResult result = ponder.stopAndFind(player, opponent);
        ponder.stop();
        assertNotNull(result);
        assertTrue((ZebraBitBoard.legalMoves(player, opponent) & (1L << result.move())) != 0L);
        assertTrue(result.completedDepth() >= 1);
    }
}
