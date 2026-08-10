package com.zawa.client.ai.research;

import com.zawa.client.ai.AbstractClientAi;
import java.util.Arrays;
import java.util.Random;

public class ClientTacticalBitboardAI extends AbstractClientAi {

    private static final int TIME_LIMIT_MS = 2400;
    private static final int EXACT_WIN_SCORE = 100000;
    private static final int ENDGAME_START_EMPTIES = 22;

    private static final long[] ZOBRIST_B = new long[64];
    private static final long[] ZOBRIST_W = new long[64];
    private static final long ZOBRIST_TURN;

    private static final int TT_SIZE = 1 << 21;
    private static final int TT_MASK = TT_SIZE - 1;
    private final long[] ttLocks = new long[TT_SIZE];
    private final long[] ttData = new long[TT_SIZE];

    private long endTime;
    private int nodeCount;

    private static class TimeOutException extends RuntimeException {}

    private static final int[] MOVE_ORDER_WEIGHTS = {
            10000, -3000, 1000,  800,  800, 1000, -3000, 10000,
            -3000, -5000, -450, -500, -500, -450, -5000, -3000,
            1000,  -450,   30,   10,   10,   30,  -450,  1000,
            800,  -500,   10,   50,   50,   10,  -500,   800,
            800,  -500,   10,   50,   50,   10,  -500,   800,
            1000,  -450,   30,   10,   10,   30,  -450,  1000,
            -3000, -5000, -450, -500, -500, -450, -5000, -3000,
            10000, -3000, 1000,  800,  800, 1000, -3000, 10000
    };

    static {
        Random rnd = new Random(2026);
        for (int i = 0; i < 64; i++) {
            ZOBRIST_B[i] = rnd.nextLong();
            ZOBRIST_W[i] = rnd.nextLong();
        }
        ZOBRIST_TURN = rnd.nextLong();
    }

    public ClientTacticalBitboardAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
        this.ai_name = "TacticalBitboardAI";
    }

    public ClientTacticalBitboardAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "TacticalBitboardAI";
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
        if (legalMoves == 0L) return null;

        int emptyCount = 64 - Long.bitCount(myBoard | oppBoard);
        Arrays.fill(ttLocks, 0L);
        Arrays.fill(ttData, 0L);

        nodeCount = 0;
        endTime = System.currentTimeMillis() + TIME_LIMIT_MS;

        int bestMove = Long.numberOfTrailingZeros(legalMoves);
        int maxDepth = emptyCount <= ENDGAME_START_EMPTIES ? emptyCount : 60;

        try {
            for (int depth = 1; depth <= maxDepth; depth++) {
                int currentBestMove = searchRoot(myBoard, oppBoard, legalMoves, depth, emptyCount);
                if (currentBestMove != -1) {
                    bestMove = currentBestMove;
                }
                if (emptyCount <= ENDGAME_START_EMPTIES && depth == emptyCount) {
                    break;
                }
            }
        } catch (TimeOutException e) {
        }

        return new Integer[]{bestMove / 8, bestMove % 8};
    }

    private int searchRoot(long myBoard, long oppBoard, long legalMoves, int depth, int emptyCount) {
        int alpha = -EXACT_WIN_SCORE * 2;
        int beta = EXACT_WIN_SCORE * 2;
        int bestMove = -1;
        int bestScore = -EXACT_WIN_SCORE * 2;

        long hash = computeHash(myBoard, oppBoard, true);
        int ttMove = getTTMove(hash);

        long[] sortedMoves = getSortedMoves(myBoard, oppBoard, legalMoves, ttMove);

        for (int i = 0; i < sortedMoves.length; i++) {
            int move = (int) (sortedMoves[i] & 0xFFFFFFFFL);
            long flip = getFlip(myBoard, oppBoard, move);
            int score;

            if (emptyCount <= ENDGAME_START_EMPTIES && depth == emptyCount) {
                if (bestMove == -1) {
                    score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -beta, -alpha, emptyCount - 1, false);
                } else {
                    score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -alpha - 1, -alpha, emptyCount - 1, false);
                    if (alpha < score && score < beta) {
                        score = -exactSearch(oppBoard ^ flip, myBoard | (1L << move) | flip, -beta, -score, emptyCount - 1, false);
                    }
                }
            } else {
                if (bestMove == -1) {
                    score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -beta, -alpha, emptyCount - 1, false);
                } else {
                    score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -alpha - 1, -alpha, emptyCount - 1, false);
                    if (alpha < score && score < beta) {
                        score = -negaScout(oppBoard ^ flip, myBoard | (1L << move) | flip, depth - 1, -beta, -score, emptyCount - 1, false);
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestScore);
        }

        if (bestMove != -1) {
            storeTT(hash, depth, bestScore, 0, bestMove);
        }

        return bestMove;
    }

    private int negaScout(long myBoard, long oppBoard, int depth, int alpha, int beta, int emptyCount, boolean passed) {
        checkTime();

        if (depth == 0 || emptyCount == 0) {
            return evaluateTactical(myBoard, oppBoard, emptyCount);
        }

        long hash = computeHash(myBoard, oppBoard, !passed);
        int alphaOrig = alpha;

        int ttScore = getTTScore(hash, depth, alpha, beta);
        if (ttScore != -EXACT_WIN_SCORE * 3) {
            return ttScore;
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

        int bestScore = -EXACT_WIN_SCORE * 2;
        int bestMove = -1;
        boolean first = true;
        int ttMove = getTTMove(hash);

        long[] sortedMoves = getSortedMoves(myBoard, oppBoard, legalMoves, ttMove);

        for (int i = 0; i < sortedMoves.length; i++) {
            int move = (int) (sortedMoves[i] & 0xFFFFFFFFL);
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
        checkTime();

        if (emptyCount == 0) {
            return Long.bitCount(myBoard) - Long.bitCount(oppBoard);
        }

        long legalMoves = getLegalMoves(myBoard, oppBoard);
        if (legalMoves == 0L) {
            if (passed) {
                int diff = Long.bitCount(myBoard) - Long.bitCount(oppBoard);
                if (diff > 0) return diff + emptyCount;
                if (diff < 0) return diff - emptyCount;
                return diff;
            }
            return -exactSearch(oppBoard, myBoard, -beta, -alpha, emptyCount, true);
        }

        long hash = computeHash(myBoard, oppBoard, !passed);
        int alphaOrig = alpha;

        int ttScore = getTTScore(hash, emptyCount + 100, alpha, beta);
        if (ttScore != -EXACT_WIN_SCORE * 3) {
            return ttScore;
        }

        int bestScore = -64;
        int bestMove = -1;
        boolean first = true;
        int ttMove = getTTMove(hash);

        long[] sortedMoves = getSortedMoves(myBoard, oppBoard, legalMoves, ttMove);

        for (int i = 0; i < sortedMoves.length; i++) {
            int move = (int) (sortedMoves[i] & 0xFFFFFFFFL);
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
        storeTT(hash, emptyCount + 100, bestScore, flag, bestMove);

        return bestScore;
    }

    private void checkTime() {
        if ((++nodeCount & 4095) == 0) {
            if (System.currentTimeMillis() >= endTime) {
                throw new TimeOutException();
            }
        }
    }

    private long[] getSortedMoves(long myBoard, long oppBoard, long legalMoves, int ttMove) {
        int count = Long.bitCount(legalMoves);
        long[] moves = new long[count];
        int idx = 0;
        long temp = legalMoves;
        while (temp != 0L) {
            int m = Long.numberOfTrailingZeros(temp);
            temp &= temp - 1L;

            long p = 1L << m;
            long flip = getFlip(myBoard, oppBoard, m);
            long nextMy = oppBoard ^ flip;
            long nextOpp = myBoard | p | flip;

            int oppMob = Long.bitCount(getLegalMoves(nextMy, nextOpp));
            int score = MOVE_ORDER_WEIGHTS[m] - oppMob * 50;

            if (m == ttMove) score += 1000000;
            moves[idx++] = ((long) score << 32) | m;
        }
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (moves[i] < moves[j]) {
                    long t = moves[i];
                    moves[i] = moves[j];
                    moves[j] = t;
                }
            }
        }
        return moves;
    }

    private int evaluateTactical(long myBoard, long oppBoard, int emptyCount) {
        long empty = ~(myBoard | oppBoard);

        long myLegal = getLegalMoves(myBoard, oppBoard);
        long oppLegal = getLegalMoves(oppBoard, myBoard);
        int myMob = Long.bitCount(myLegal);
        int oppMob = Long.bitCount(oppLegal);

        long myFrontier = getFrontier(myBoard, empty);
        long oppFrontier = getFrontier(oppBoard, empty);
        int myFrontierCount = Long.bitCount(myFrontier);
        int oppFrontierCount = Long.bitCount(oppFrontier);

        int myCount = Long.bitCount(myBoard);
        int oppCount = Long.bitCount(oppBoard);

        double phase = (double) emptyCount / 64.0;

        int mobWeight = (int)(150 + 100 * phase);
        int frontWeight = (int)(50 + 50 * phase);
        int countWeight = (int)(-40 * phase + 20 * (1.0 - phase));

        int score = (myMob - oppMob) * mobWeight;
        score += (oppFrontierCount - myFrontierCount) * frontWeight;
        score += (myCount - oppCount) * countWeight;

        long corners = 0x8100000000000081L;
        long myCorners = myBoard & corners;
        long oppCorners = oppBoard & corners;
        score += Long.bitCount(myCorners) * 6000;
        score -= Long.bitCount(oppCorners) * 6000;

        long emptyCorners = empty & corners;
        if (emptyCorners != 0) {
            if ((emptyCorners & 0x0100000000000000L) != 0) {
                if ((myBoard & 0x0203000000000000L) != 0) score -= 2000;
                if ((oppBoard & 0x0203000000000000L) != 0) score += 2000;
            }
            if ((emptyCorners & 0x8000000000000000L) != 0) {
                if ((myBoard & 0x40C0000000000000L) != 0) score -= 2000;
                if ((oppBoard & 0x40C0000000000000L) != 0) score += 2000;
            }
            if ((emptyCorners & 0x0000000000000001L) != 0) {
                if ((myBoard & 0x0000000000000302L) != 0) score -= 2000;
                if ((oppBoard & 0x0000000000000302L) != 0) score += 2000;
            }
            if ((emptyCorners & 0x0000000000000080L) != 0) {
                if ((myBoard & 0x000000000000C040L) != 0) score -= 2000;
                if ((oppBoard & 0x000000000000C040L) != 0) score += 2000;
            }
        }

        if (emptyCount % 2 == 1) {
            score += 300;
        } else {
            score -= 300;
        }

        return score;
    }

    private long getFrontier(long board, long empty) {
        long f = 0L;
        f |= (board << 1) & empty & 0xFEFEFEFEFEFEFEFEL;
        f |= (board >>> 1) & empty & 0x7F7F7F7F7F7F7F7FL;
        f |= (board << 8) & empty;
        f |= (board >>> 8) & empty;
        f |= (board << 7) & empty & 0x7F7F7F7F7F7F7F7FL;
        f |= (board >>> 7) & empty & 0xFEFEFEFEFEFEFEFEL;
        f |= (board << 9) & empty & 0xFEFEFEFEFEFEFEFEL;
        f |= (board >>> 9) & empty & 0x7F7F7F7F7F7F7F7FL;
        return f;
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
        long p = 1L << move;

        long mask = opp & 0x7E7E7E7E7E7E7E7EL;
        long out = mask & (p >>> 1);
        out |= mask & (out >>> 1); out |= mask & (out >>> 1); out |= mask & (out >>> 1); out |= mask & (out >>> 1); out |= mask & (out >>> 1);
        if ((my & (out >>> 1)) != 0) flip |= out;

        out = mask & (p << 1);
        out |= mask & (out << 1); out |= mask & (out << 1); out |= mask & (out << 1); out |= mask & (out << 1); out |= mask & (out << 1);
        if ((my & (out << 1)) != 0) flip |= out;

        mask = opp & 0x00FFFFFFFFFFFF00L;
        out = mask & (p >>> 8);
        out |= mask & (out >>> 8); out |= mask & (out >>> 8); out |= mask & (out >>> 8); out |= mask & (out >>> 8); out |= mask & (out >>> 8);
        if ((my & (out >>> 8)) != 0) flip |= out;

        out = mask & (p << 8);
        out |= mask & (out << 8); out |= mask & (out << 8); out |= mask & (out << 8); out |= mask & (out << 8); out |= mask & (out << 8);
        if ((my & (out << 8)) != 0) flip |= out;

        mask = opp & 0x007E7E7E7E7E7E00L;
        out = mask & (p >>> 9);
        out |= mask & (out >>> 9); out |= mask & (out >>> 9); out |= mask & (out >>> 9); out |= mask & (out >>> 9); out |= mask & (out >>> 9);
        if ((my & (out >>> 9)) != 0) flip |= out;

        out = mask & (p << 9);
        out |= mask & (out << 9); out |= mask & (out << 9); out |= mask & (out << 9); out |= mask & (out << 9); out |= mask & (out << 9);
        if ((my & (out << 9)) != 0) flip |= out;

        out = mask & (p >>> 7);
        out |= mask & (out >>> 7); out |= mask & (out >>> 7); out |= mask & (out >>> 7); out |= mask & (out >>> 7); out |= mask & (out >>> 7);
        if ((my & (out >>> 7)) != 0) flip |= out;

        out = mask & (p << 7);
        out |= mask & (out << 7); out |= mask & (out << 7); out |= mask & (out << 7); out |= mask & (out << 7); out |= mask & (out << 7);
        if ((my & (out << 7)) != 0) flip |= out;

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
        ttLocks[idx] = hash;
        long m = (move == -1) ? 64L : (long) move;
        ttData[idx] = ((long) depth << 56) | ((long) flag << 54) | (m << 40) | (score & 0xFFFFFFFFL);
    }

    private int getTTScore(long hash, int depth, int alpha, int beta) {
        int idx = (int) (hash & TT_MASK);
        if (ttLocks[idx] == hash) {
            long data = ttData[idx];
            int ttDepth = (int) ((data >>> 56) & 0xFF);
            if (ttDepth >= depth) {
                int ttScore = (int) data;
                int ttFlag = (int) ((data >>> 54) & 0x3);
                if (ttFlag == 0) return ttScore;
                if (ttFlag == 1 && ttScore <= alpha) return alpha;
                if (ttFlag == 2 && ttScore >= beta) return beta;
            }
        }
        return -EXACT_WIN_SCORE * 3;
    }

    private int getTTMove(long hash) {
        int idx = (int) (hash & TT_MASK);
        if (ttLocks[idx] == hash) {
            int m = (int) ((ttData[idx] >>> 40) & 0x7F);
            return m == 64 ? -1 : m;
        }
        return -1;
    }
}