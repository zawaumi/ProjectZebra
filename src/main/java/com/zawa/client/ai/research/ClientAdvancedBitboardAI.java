package com.zawa.client.ai.research;

import com.zawa.client.ai.AbstractClientAi;

import java.util.Arrays;
import java.util.Random;

public class ClientAdvancedBitboardAI extends AbstractClientAi {

    private static final int MAX_MIDGAME_DEPTH = 10;
    private static final int ENDGAME_DEPTH = 18;
    private static final int EXACT_WIN_SCORE = 10000;

    private static final long[] ZOBRIST_B = new long[64];
    private static final long[] ZOBRIST_W = new long[64];
    private static final long ZOBRIST_TURN;

    private static final int TT_SIZE = 1 << 20;
    private static final int TT_MASK = TT_SIZE - 1;
    private final long[] ttLocks = new long[TT_SIZE];
    private final long[] ttData = new long[TT_SIZE];

    static {
        Random rnd = new Random(2026);
        for (int i = 0; i < 64; i++) {
            ZOBRIST_B[i] = rnd.nextLong();
            ZOBRIST_W[i] = rnd.nextLong();
        }
        ZOBRIST_TURN = rnd.nextLong();
    }

    public ClientAdvancedBitboardAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
        this.ai_name = "AdvancedBitboardAI";
    }

    public ClientAdvancedBitboardAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "AdvancedBitboardAI";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        long myBoard = 0L;
        long oppBoard = 0L;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (othello_array[r][c] != null) {
                    long bit = 1L << (r * 8 + c);
                    if (othello_array[r][c].equals(myturn)) {
                        myBoard |= bit;
                    } else if (othello_array[r][c].equals(-myturn)) {
                        oppBoard |= bit;
                    }
                }
            }
        }

        long legalMoves = getLegalMoves(myBoard, oppBoard);
        if (legalMoves == 0L) {
            return null;
        }

        int emptyCount = 64 - Long.bitCount(myBoard | oppBoard);
        Arrays.fill(ttLocks, 0L);
        Arrays.fill(ttData, 0L);

        int bestMove = -1;

        if (emptyCount <= ENDGAME_DEPTH) {
            bestMove = solveEndgame(myBoard, oppBoard, legalMoves, emptyCount);
        } else {
            bestMove = searchMidgame(myBoard, oppBoard, legalMoves, emptyCount);
        }

        if (bestMove == -1) {
            bestMove = Long.numberOfTrailingZeros(legalMoves);
        }

        return new Integer[]{bestMove / 8, bestMove % 8};
    }

    private int searchMidgame(long myBoard, long oppBoard, long legalMoves, int emptyCount) {
        int bestMove = -1;
        int maxDepth = Math.min(MAX_MIDGAME_DEPTH, emptyCount);

        for (int depth = 1; depth <= maxDepth; depth++) {
            int alpha = -20000;
            int beta = 20000;
            int currentBestMove = -1;
            int bestScore = -20000;

            long moves = legalMoves;
            long hash = computeHash(myBoard, oppBoard, true);

            int ttMove = getTTMove(hash);
            if (ttMove != -1 && (legalMoves & (1L << ttMove)) != 0) {
                long flip = getFlip(myBoard, oppBoard, ttMove);
                int score = -negaScout(oppBoard ^ flip, myBoard | (1L << ttMove) | flip, depth - 1, -beta, -alpha, emptyCount - 1, false);
                if (score > bestScore) {
                    bestScore = score;
                    currentBestMove = ttMove;
                }
                alpha = Math.max(alpha, bestScore);
                moves &= ~(1L << ttMove);
            }

            while (moves != 0L) {
                int move = Long.numberOfTrailingZeros(moves);
                moves &= moves - 1L;
                long flip = getFlip(myBoard, oppBoard, move);
                int score;
                if (currentBestMove == -1) {
                    score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -beta, -alpha, emptyCount - 1, false);
                } else {
                    score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -alpha - 1, -alpha, emptyCount - 1, false);
                    if (alpha < score && score < beta) {
                        score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -beta, -score, emptyCount - 1, false);
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    currentBestMove = move;
                }
                alpha = Math.max(alpha, bestScore);
            }
            if (currentBestMove != -1) {
                bestMove = currentBestMove;
            }
        }
        return bestMove;
    }

    private int solveEndgame(long myBoard, long oppBoard, long legalMoves, int emptyCount) {
        int alpha = -64;
        int beta = 64;
        int bestMove = -1;
        int bestScore = -100;

        long moves = sortMovesEndgame(myBoard, oppBoard, legalMoves);

        while (moves != 0L) {
            int move = Long.numberOfTrailingZeros(moves);
            moves &= moves - 1L;
            long flip = getFlip(myBoard, oppBoard, move);
            int score;
            if (bestMove == -1) {
                score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -beta, -alpha, emptyCount - 1, false);
            } else {
                score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -alpha - 1, -alpha, emptyCount - 1, false);
                if (alpha < score && score < beta) {
                    score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -beta, -score, emptyCount - 1, false);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return bestMove;
    }

    private int negaScout(long myBoard, long oppBoard, int depth, int alpha, int beta, int emptyCount, boolean passed) {
        if (depth == 0 || emptyCount == 0) {
            return evaluateBoard(myBoard, oppBoard);
        }

        long hash = computeHash(myBoard, oppBoard, !passed);
        int alphaOrig = alpha;
        long ttVal = getTTData(hash);
        if (ttVal != 0L) {
            int ttDepth = (int) ((ttVal >>> 32) & 0xFF);
            if (ttDepth >= depth) {
                int ttScore = (short) (ttVal & 0xFFFF);
                int ttFlag = (int) ((ttVal >>> 16) & 0x3);
                if (ttFlag == 0) return ttScore;
                if (ttFlag == 1 && ttScore <= alpha) return alpha;
                if (ttFlag == 2 && ttScore >= beta) return beta;
            }
        }

        long legalMoves = getLegalMoves(myBoard, oppBoard);
        if (legalMoves == 0L) {
            if (passed) {
                int myCount = Long.bitCount(myBoard);
                int oppCount = Long.bitCount(oppBoard);
                return (myCount > oppCount) ? EXACT_WIN_SCORE + (myCount - oppCount) : (myCount < oppCount) ? -EXACT_WIN_SCORE + (myCount - oppCount) : 0;
            }
            return -negaScout(oppBoard, myBoard, depth, -beta, -alpha, emptyCount, true);
        }

        int bestScore = -20000;
        int bestMove = -1;
        boolean first = true;

        long moves = legalMoves;
        int ttMove = getTTMove(hash);
        if (ttMove != -1 && (legalMoves & (1L << ttMove)) != 0) {
            long flip = getFlip(myBoard, oppBoard, ttMove);
            bestScore = -negaScout(oppBoard ^ flip, myBoard | (1L << ttMove) | flip, depth - 1, -beta, -alpha, emptyCount - 1, false);
            bestMove = ttMove;
            alpha = Math.max(alpha, bestScore);
            first = false;
            moves &= ~(1L << ttMove);
            if (alpha >= beta) {
                storeTT(hash, depth, bestScore, 1, bestMove);
                return bestScore;
            }
        }

        while (moves != 0L) {
            int move = Long.numberOfTrailingZeros(moves);
            moves &= moves - 1L;
            long flip = getFlip(myBoard, oppBoard, move);
            int score;
            if (first) {
                score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -beta, -alpha, emptyCount - 1, false);
                first = false;
            } else {
                score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -alpha - 1, -alpha, emptyCount - 1, false);
                if (alpha < score && score < beta) {
                    score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -beta, -score, emptyCount - 1, false);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestScore);
            if (alpha >= beta) break;
        }

        int flag = 0;
        if (bestScore <= alphaOrig) flag = 1;
        else if (bestScore >= beta) flag = 2;
        storeTT(hash, depth, bestScore, flag, bestMove);

        return bestScore;
    }

    private int exactSearch(long myBoard, long oppBoard, int alpha, int beta, int emptyCount, boolean passed) {
        if (emptyCount == 0) {
            return Long.bitCount(myBoard) - Long.bitCount(oppBoard);
        }

        long legalMoves = getLegalMoves(myBoard, oppBoard);
        if (legalMoves == 0L) {
            if (passed) {
                return Long.bitCount(myBoard) - Long.bitCount(oppBoard);
            }
            return -exactSearch(oppBoard, myBoard, -beta, -alpha, emptyCount, true);
        }

        int bestScore = -64;
        long moves = legalMoves;
        boolean first = true;

        while (moves != 0L) {
            int move = Long.numberOfTrailingZeros(moves);
            moves &= moves - 1L;
            long flip = getFlip(myBoard, oppBoard, move);
            int score;
            if (first) {
                score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -beta, -alpha, emptyCount - 1, false);
                first = false;
            } else {
                score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -alpha - 1, -alpha, emptyCount - 1, false);
                if (alpha < score && score < beta) {
                    score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -beta, -score, emptyCount - 1, false);
                }
            }
            if (score > bestScore) bestScore = score;
            alpha = Math.max(alpha, bestScore);
            if (alpha >= beta) break;
        }
        return bestScore;
    }

    private int evaluateBoard(long myBoard, long oppBoard) {
        int myMob = Long.bitCount(getLegalMoves(myBoard, oppBoard));
        int oppMob = Long.bitCount(getLegalMoves(oppBoard, myBoard));
        int score = (myMob - oppMob) * 10;
        long corners = 0x8100000000000081L;
        long myCorners = myBoard & corners;
        long oppCorners = oppBoard & corners;
        score += Long.bitCount(myCorners) * 500;
        score -= Long.bitCount(oppCorners) * 500;
        long xSquares = 0x0042000000004200L;
        long myX = myBoard & xSquares;
        long oppX = oppBoard & xSquares;
        if ((myCorners & 0x8000000000000000L) == 0 && (myX & 0x0040000000000000L) != 0) score -= 300;
        if ((myCorners & 0x0100000000000000L) == 0 && (myX & 0x0002000000000000L) != 0) score -= 300;
        if ((myCorners & 0x0000000000000080L) == 0 && (myX & 0x0000000000004000L) != 0) score -= 300;
        if ((myCorners & 0x0000000000000001L) == 0 && (myX & 0x0000000000000002L) != 0) score -= 300;
        if ((oppCorners & 0x8000000000000000L) == 0 && (oppX & 0x0040000000000000L) != 0) score += 300;
        if ((oppCorners & 0x0100000000000000L) == 0 && (oppX & 0x0002000000000000L) != 0) score += 300;
        if ((oppCorners & 0x0000000000000080L) == 0 && (oppX & 0x0000000000004000L) != 0) score += 300;
        if ((oppCorners & 0x0000000000000001L) == 0 && (oppX & 0x0000000000000002L) != 0) score += 300;
        return score;
    }

    private long sortMovesEndgame(long myBoard, long oppBoard, long legalMoves) {
        return legalMoves;
    }

    private long getLegalMoves(long my, long opp) {
        long empty = ~(my | opp);
        long legal = 0L;
        long w = opp & 0x7E7E7E7E7E7E7E7EL;
        long t = w & (my >>> 1);
        t |= w & (t >>> 1); t |= w & (t >>> 1); t |= w & (t >>> 1); t |= w & (t >>> 1); t |= w & (t >>> 1);
        legal |= empty & (t >>> 1);
        t = w & (my << 1);
        t |= w & (t << 1); t |= w & (t << 1); t |= w & (t << 1); t |= w & (t << 1); t |= w & (t << 1);
        legal |= empty & (t << 1);
        w = opp & 0x00FFFFFFFFFFFF00L;
        t = w & (my >>> 8);
        t |= w & (t >>> 8); t |= w & (t >>> 8); t |= w & (t >>> 8); t |= w & (t >>> 8); t |= w & (t >>> 8);
        legal |= empty & (t >>> 8);
        t = w & (my << 8);
        t |= w & (t << 8); t |= w & (t << 8); t |= w & (t << 8); t |= w & (t << 8); t |= w & (t << 8);
        legal |= empty & (t << 8);
        w = opp & 0x007E7E7E7E7E7E00L;
        t = w & (my >>> 7);
        t |= w & (t >>> 7); t |= w & (t >>> 7); t |= w & (t >>> 7); t |= w & (t >>> 7); t |= w & (t >>> 7);
        legal |= empty & (t >>> 7);
        t = w & (my << 7);
        t |= w & (t << 7); t |= w & (t << 7); t |= w & (t << 7); t |= w & (t << 7); t |= w & (t << 7);
        legal |= empty & (t << 7);
        t = w & (my >>> 9);
        t |= w & (t >>> 9); t |= w & (t >>> 9); t |= w & (t >>> 9); t |= w & (t >>> 9); t |= w & (t >>> 9);
        legal |= empty & (t >>> 9);
        t = w & (my << 9);
        t |= w & (t << 9); t |= w & (t << 9); t |= w & (t << 9); t |= w & (t << 9); t |= w & (t << 9);
        legal |= empty & (t << 9);
        return legal;
    }

    private long getFlip(long my, long opp, int move) {
        long flip = 0L;
        long mask = 1L << move;
        long t;
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask >>> i;
            if ((s & 0x7F7F7F7F7F7F7F7FL) == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask << i;
            if ((s & 0xFEFEFEFEFEFEFEFEL) == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask >>> (i * 8);
            if (s == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask << (i * 8);
            if (s == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask >>> (i * 7);
            if ((s & 0xFEFEFEFEFEFEFEFEL) == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask << (i * 7);
            if ((s & 0x7F7F7F7F7F7F7F7FL) == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask >>> (i * 9);
            if ((s & 0x7F7F7F7F7F7F7F7FL) == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        t = 0L;
        for (int i = 1; i <= 7; i++) {
            long s = mask << (i * 9);
            if ((s & 0xFEFEFEFEFEFEFEFEL) == 0) break;
            if ((s & opp) != 0) t |= s;
            else { if ((s & my) != 0) flip |= t; break; }
        }
        return flip;
    }

    private long computeHash(long my, long opp, boolean isMyTurn) {
        long h = 0L;
        long m = my;
        while (m != 0L) {
            int idx = Long.numberOfTrailingZeros(m);
            h ^= ZOBRIST_B[idx];
            m &= m - 1L;
        }
        long o = opp;
        while (o != 0L) {
            int idx = Long.numberOfTrailingZeros(o);
            h ^= ZOBRIST_W[idx];
            o &= o - 1L;
        }
        if (!isMyTurn) h ^= ZOBRIST_TURN;
        return h;
    }

    private void storeTT(long hash, int depth, int score, int flag, int move) {
        int idx = (int) (hash & TT_MASK);
        long lock = hash ^ (hash >>> 32);
        long data = ((long) depth << 32) | ((long) flag << 16) | ((long) (score & 0xFFFF));
        if (move != -1) data |= ((long) move << 40);
        ttLocks[idx] = lock;
        ttData[idx] = data;
    }

    private long getTTData(long hash) {
        int idx = (int) (hash & TT_MASK);
        long lock = hash ^ (hash >>> 32);
        if (ttLocks[idx] == lock) return ttData[idx];
        return 0L;
    }

    private int getTTMove(long hash) {
        long data = getTTData(hash);
        if (data != 0L) return (int) ((data >>> 40) & 0x3F);
        return -1;
    }
}