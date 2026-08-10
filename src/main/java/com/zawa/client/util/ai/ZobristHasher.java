package com.zawa.client.util.ai;

import java.util.Random;

public class ZobristHasher {
    private static final long[] ZOBRIST_BLACK = new long[64];
    private static final long[] ZOBRIST_WHITE = new long[64];
    private static final long ZOBRIST_TURN;

    static {
        Random random = new Random(2026);
        for (int i = 0; i < 64; i++) {
            ZOBRIST_BLACK[i] = random.nextLong();
            ZOBRIST_WHITE[i] = random.nextLong();
        }
        ZOBRIST_TURN = random.nextLong();
    }

    public static long computeHash(long myBoard, long opponentBoard, boolean isMyTurn) {
        long hashValue = 0L;
        long currentMyBoard = myBoard;
        while (currentMyBoard != 0L) {
            int index = Long.numberOfTrailingZeros(currentMyBoard);
            hashValue ^= ZOBRIST_BLACK[index];
            currentMyBoard &= currentMyBoard - 1L;
        }
        long currentOpponentBoard = opponentBoard;
        while (currentOpponentBoard != 0L) {
            int index = Long.numberOfTrailingZeros(currentOpponentBoard);
            hashValue ^= ZOBRIST_WHITE[index];
            currentOpponentBoard &= currentOpponentBoard - 1L;
        }
        if (!isMyTurn) {
            hashValue ^= ZOBRIST_TURN;
        }
        return hashValue;
    }
}