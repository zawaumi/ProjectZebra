package com.zawa.client.ai.zebra;

final class ZebraEndgameTailSolver {
    private static final int INFINITY = 31_500;
    private static final int WIN_SCORE = 30_000;

    private ZebraEndgameTailSolver() {
    }

    static int solve(long player, long opponent, int alpha, int beta, boolean passed, ZebraSearchClock clock) {
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        if (legal == 0L) {
            long opponentLegal = ZebraBitBoard.legalMoves(opponent, player);
            if (passed || opponentLegal == 0L) {
                return terminalScore(player, opponent);
            }
            clock.visit();
            return -solve(opponent, player, -beta, -alpha, true, clock);
        }
        int best = -INFINITY;
        while (legal != 0L) {
            int move = Long.numberOfTrailingZeros(legal);
            legal &= legal - 1L;
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            long nextPlayer = opponent ^ flipped;
            long nextOpponent = player | flipped | (1L << move);
            clock.visit();
            int score = -solve(nextPlayer, nextOpponent, -beta, -alpha, false, clock);
            best = Math.max(best, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    private static int terminalScore(long player, long opponent) {
        int difference = Long.bitCount(player) - Long.bitCount(opponent);
        if (difference > 0) {
            return WIN_SCORE + difference;
        }
        if (difference < 0) {
            return -WIN_SCORE + difference;
        }
        return 0;
    }
}
