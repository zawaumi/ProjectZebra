package com.zawa.client.ai.research;

import com.zawa.client.ai.AbstractClientAi;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class ClientAreaBitboardAI extends AbstractClientAi {

    private static final int TIME_LIMIT_MS = 2500;
    private static final int EXACT_WIN_SCORE = 30000;
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

    private static final int[] CELL_WEIGHT = {
         30, -12,   0,  -1,  -1,   0, -12,  30,
        -12, -15,  -3,  -3,  -3,  -3, -15, -12,
          0,  -3,   0,  -1,  -1,   0,  -3,   0,
         -1,  -3,  -1,  -1,  -1,  -1,  -3,  -1,
         -1,  -3,  -1,  -1,  -1,  -1,  -3,  -1,
          0,  -3,   0,  -1,  -1,   0,  -3,   0,
        -12, -15,  -3,  -3,  -3,  -3, -15, -12,
         30, -12,   0,  -1,  -1,   0, -12,  30,
    };

    private static final int[] WEIGHT_VALUES;
    private static final long[] WEIGHT_MASKS;

    private static final long EDGE_TOP    = 0x00000000000000ffL;
    private static final long EDGE_BOTTOM = 0xff00000000000000L;
    private static final long EDGE_LEFT   = 0x0101010101010101L;
    private static final long EDGE_RIGHT  = 0x8080808080808080L;
    private static final long CORNER_BLOCKS = 0xc3c300000000c3c3L;

    private static final int[] PATTERN_SCORE = new int[81];
    private static final int[][] BLOCKS = {
        {0, 1, 8, 9},
        {7, 6, 15, 14},
        {56, 57, 48, 49},
        {63, 62, 55, 54},
    };

    static {
        Random rnd = new Random(2026);
        for (int i = 0; i < 64; i++) {
            ZOBRIST_B[i] = rnd.nextLong();
            ZOBRIST_W[i] = rnd.nextLong();
        }
        ZOBRIST_TURN = rnd.nextLong();

        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < 64; i++) {
            long mask = 1L << i;
            map.put(CELL_WEIGHT[i], map.getOrDefault(CELL_WEIGHT[i], 0L) | mask);
        }
        WEIGHT_VALUES = new int[map.size()];
        WEIGHT_MASKS = new long[map.size()];
        int k = 0;
        for (Map.Entry<Integer, Long> e : map.entrySet()) {
            WEIGHT_VALUES[k] = e.getKey();
            WEIGHT_MASKS[k] = e.getValue();
            k++;
        }

        for (int corner = 0; corner < 3; corner++) {
            for (int c1 = 0; c1 < 3; c1++) {
                for (int c2 = 0; c2 < 3; c2++) {
                    for (int x = 0; x < 3; x++) {
                        int index = corner * 27 + c1 * 9 + c2 * 3 + x;
                        PATTERN_SCORE[index] = cornerScore(corner)
                                + cScore(corner, c1) + cScore(corner, c2)
                                + xScore(corner, x);
                    }
                }
            }
        }
    }

    private static int cornerScore(int corner) {
        if (corner == 1) return 45;
        if (corner == 2) return -45;
        return 0;
    }

    private static int xScore(int corner, int x) {
        if (x == 0) return 0;
        if (corner == 0) return x == 1 ? -35 : 35;
        if (corner == 1) return x == 1 ? 8 : -8;
        return x == 1 ? 4 : -4;
    }

    private static int cScore(int corner, int c) {
        if (c == 0) return 0;
        if (corner == 0) return c == 1 ? -12 : 12;
        if (corner == 1) return c == 1 ? 10 : -10;
        return c == 1 ? -2 : 2;
    }

    public ClientAreaBitboardAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
        this.ai_name = "AreaBitboardAI";
    }

    public ClientAreaBitboardAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "AreaBitboardAI";
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
                if (emptyCount <= ENDGAME_START_EMPTIES && depth == emptyCount) break;
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
        long[] sortedMoves = getSortedMoves(legalMoves, ttMove, myBoard, oppBoard);

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
            return evaluateArea(myBoard, oppBoard, emptyCount);
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
                return calculateFinalScore(myBoard, oppBoard);
            }
            return -negaScout(oppBoard, myBoard, depth, -beta, -alpha, emptyCount, true);
        }

        int bestScore = -EXACT_WIN_SCORE * 2;
        int bestMove = -1;
        boolean first = true;
        int ttMove = (ttVal != 0L) ? (int) ((ttVal >>> 40) & 0x3F) : -1;

        long[] sortedMoves = getSortedMoves(legalMoves, ttMove, myBoard, oppBoard);

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
                return 0;
            }
            return -exactSearch(oppBoard, myBoard, -beta, -alpha, emptyCount, true);
        }

        if (emptyCount == 1) {
            int m = Long.numberOfTrailingZeros(legalMoves);
            long fl = getFlip(myBoard, oppBoard, m);
            return Long.bitCount(myBoard | (1L << m) | fl) * 2 - 64;
        }

        int bestScore = -64;
        boolean first = true;
        long hash = computeHash(myBoard, oppBoard, !passed);
        int ttMove = getTTMove(hash);

        long[] sortedMoves = getSortedMoves(legalMoves, ttMove, myBoard, oppBoard);

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
            if (score > bestScore) bestScore = score;
            alpha = Math.max(alpha, bestScore);
            if (alpha >= beta) break;
        }
        return bestScore;
    }

    private void checkTime() {
        if ((++nodeCount & 4095) == 0) {
            if (System.currentTimeMillis() >= endTime) {
                throw new TimeOutException();
            }
        }
    }

    private int calculateFinalScore(long myBoard, long oppBoard) {
        int pc = Long.bitCount(myBoard);
        int oc = Long.bitCount(oppBoard);
        int diff = pc - oc;
        int e = 64 - pc - oc;
        int rawScore = 0;
        if (diff > 0) rawScore = diff + e;
        else if (diff < 0) rawScore = diff - e;
        
        if (rawScore > 0) return rawScore * 100 + 10000;
        if (rawScore < 0) return rawScore * 100 - 10000;
        return 0;
    }

    private long[] getSortedMoves(long legalMoves, int ttMove, long myBoard, long oppBoard) {
        int count = Long.bitCount(legalMoves);
        long[] moves = new long[count];
        int idx = 0;
        long temp = legalMoves;
        while (temp != 0L) {
            int m = Long.numberOfTrailingZeros(temp);
            temp &= temp - 1L;
            long fl = getFlip(myBoard, oppBoard, m);
            long np = oppBoard ^ fl;
            long no = myBoard | (1L << m) | fl;
            int s = -Long.bitCount(getLegalMoves(np, no)) * 16 + CELL_WEIGHT[m];
            if (m == ttMove) s += 65536;
            moves[idx++] = ((long) s << 32) | m;
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

    private int evaluateArea(long p, long o, int empties) {
        long outsideCorners = ~CORNER_BLOCKS;
        int cell = cellScore(p & outsideCorners) - cellScore(o & outsideCorners);
        int mob = Long.bitCount(getLegalMoves(p, o)) - Long.bitCount(getLegalMoves(o, p));
        long all = p | o;
        int stab = Long.bitCount(stableEdgeDiscs(p, all)) - Long.bitCount(stableEdgeDiscs(o, all));
        int pattern = evaluateCornerPattern(p, o);

        int wc, wm, ws, wp;
        if (empties > 44) {
            wc = 3; wm = 90; ws = 40; wp = 5;
        } else if (empties > 28) {
            wc = 5; wm = 65; ws = 70; wp = 6;
        } else {
            wc = 4; wm = 45; ws = 95; wp = 5;
        }
        return wc * cell + wm * mob + ws * stab + wp * pattern;
    }

    private int cellScore(long stones) {
        int s = 0;
        for (int i = 0; i < WEIGHT_MASKS.length; i++) {
            s += WEIGHT_VALUES[i] * Long.bitCount(stones & WEIGHT_MASKS[i]);
        }
        return s;
    }

    private long stableEdgeDiscs(long mine, long all) {
        long s = 0;
        s |= walk(mine, 0, 1);
        s |= walk(mine, 7, -1);
        s |= walk(mine, 56, 1);
        s |= walk(mine, 63, -1);
        s |= walk(mine, 0, 8);
        s |= walk(mine, 56, -8);
        s |= walk(mine, 7, 8);
        s |= walk(mine, 63, -8);
        if ((all & EDGE_TOP) == EDGE_TOP) s |= mine & EDGE_TOP;
        if ((all & EDGE_BOTTOM) == EDGE_BOTTOM) s |= mine & EDGE_BOTTOM;
        if ((all & EDGE_LEFT) == EDGE_LEFT) s |= mine & EDGE_LEFT;
        if ((all & EDGE_RIGHT) == EDGE_RIGHT) s |= mine & EDGE_RIGHT;
        return s;
    }

    private long walk(long mine, int start, int step) {
        long s = 0;
        int idx = start;
        for (int i = 0; i < 8; i++) {
            long b = 1L << idx;
            if ((mine & b) == 0) break;
            s |= b;
            idx += step;
        }
        return s;
    }

    private int evaluateCornerPattern(long p, long o) {
        int total = 0;
        for (int[] block : BLOCKS) {
            int index = state(p, o, block[0]) * 27
                    + state(p, o, block[1]) * 9
                    + state(p, o, block[2]) * 3
                    + state(p, o, block[3]);
            total += PATTERN_SCORE[index];
        }
        return total;
    }

    private int state(long p, long o, int bit) {
        long mask = 1L << bit;
        if ((p & mask) != 0) return 1;
        if ((o & mask) != 0) return 2;
        return 0;
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