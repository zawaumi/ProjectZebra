package com.zawa.client.ai.zebra;

import java.util.Arrays;

public final class ZebraSearchEngine {
    private static final int INFINITY = 31_500;
    private static final int WIN_SCORE = 30_000;
    private static final int NO_PROBCUT = Integer.MIN_VALUE;
    private static final int[] SQUARE_ORDER = {
            4_000, -1_200, 300, 180, 180, 300, -1_200, 4_000,
            -1_200, -1_800, -120, -80, -80, -120, -1_800, -1_200,
            300, -120, 80, 35, 35, 80, -120, 300,
            180, -80, 35, 10, 10, 35, -80, 180,
            180, -80, 35, 10, 10, 35, -80, 180,
            300, -120, 80, 35, 35, 80, -120, 300,
            -1_200, -1_800, -120, -80, -80, -120, -1_800, -1_200,
            4_000, -1_200, 300, 180, 180, 300, -1_200, 4_000
    };

    private final ZebraPositionEvaluator evaluator;
    private final ZebraTranspositionTable table;
    private final ZebraSearchProfile profile;
    private final ZebraOpeningPolicy openingPolicy;
    private final ZebraSearchClock clock = new ZebraSearchClock();
    private final int[][] moveStack = new int[65][40];
    private final int[][] scoreStack = new int[65][40];
    private final int[] history = new int[64];
    private final int[] rootScores = new int[64];
    private final int[] rootIterationScores = new int[64];
    private final boolean evaluatorRequiresLegalMoves;

    private int iterationMove;
    private int iterationScore;
    private int rootPolicyMove = -1;
    private int pendingRootHint = -1;
    private int rootHintMove = -1;

    public ZebraSearchEngine(ZebraPositionEvaluator evaluator) {
        this(evaluator, 20, ZebraSearchProfile.tcl());
    }

    public ZebraSearchEngine(ZebraPositionEvaluator evaluator, int tableBits) {
        this(evaluator, tableBits, ZebraSearchProfile.tcl());
    }

    public ZebraSearchEngine(ZebraPositionEvaluator evaluator, int tableBits, ZebraSearchProfile profile) {
        this.evaluator = evaluator;
        this.table = new ZebraTranspositionTable(tableBits, profile.transpositionWays());
        this.profile = profile;
        this.openingPolicy = profile.expertOpeningPolicy() ? ZebraOpeningPolicy.defaultPolicy() : null;
        this.evaluatorRequiresLegalMoves = evaluator.requiresLegalMoves();
    }

    public void resetCaches() {
        table.clear();
        Arrays.fill(history, 0);
    }

    public void hintRootMove(int move) {
        pendingRootHint = move;
    }

    public ZebraSearchResult findBestMove(long player, long opponent, long softMilliseconds, long hardMilliseconds) {
        long started = System.nanoTime();
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        if (legal == 0L) {
            return new ZebraSearchResult(-1, 0, 0, 0L, 0L, false, false);
        }
        if ((legal & (legal - 1L)) == 0L) {
            return new ZebraSearchResult(Long.numberOfTrailingZeros(legal), 0, 0, 1L, 0L, false, false);
        }

        table.beginSearch();
        decayHistory();
        Arrays.fill(rootScores, 0);
        rootPolicyMove = openingPolicy == null ? -1 : openingPolicy.find(player, opponent);
        if (rootPolicyMove >= 0 && (legal & (1L << rootPolicyMove)) == 0L) {
            rootPolicyMove = -1;
        }
        rootHintMove = pendingRootHint;
        pendingRootHint = -1;
        if (rootHintMove >= 0 && (legal & (1L << rootHintMove)) == 0L) {
            rootHintMove = -1;
        }
        clock.start(softMilliseconds, hardMilliseconds);
        int emptyCount = 64 - Long.bitCount(player | opponent);
        int bestMove = firstOrderedMove(player, opponent, legal, emptyCount);
        int bestScore = -INFINITY;
        int completedDepth = 0;
        boolean exact = false;
        boolean outcomeSolved = false;
        try {
            if (emptyCount <= profile.exactAttemptEmpties()) {
                int fallbackDepth = emptyCount >= 21 ? 8 : emptyCount <= 12 ? 2 : 5;
                searchRoot(player, opponent, Math.min(fallbackDepth, emptyCount), -INFINITY, INFINITY, emptyCount);
                bestMove = iterationMove;
                bestScore = iterationScore;
                completedDepth = Math.min(fallbackDepth, emptyCount);
                if (completedDepth < emptyCount && !clock.softExpired()) {
                    searchRoot(player, opponent, emptyCount, 0, 1, emptyCount);
                    bestMove = iterationMove;
                    bestScore = iterationScore;
                    completedDepth = emptyCount;
                    outcomeSolved = true;
                }
                if (completedDepth == emptyCount && emptyCount <= 20 && !clock.softExpired()) {
                    searchRoot(player, opponent, emptyCount, -INFINITY, INFINITY, emptyCount);
                    bestMove = iterationMove;
                    bestScore = iterationScore;
                    completedDepth = emptyCount;
                    exact = true;
                    outcomeSolved = true;
                }
            } else {
                int lastScore = 0;
                for (int depth = 1; depth <= emptyCount; depth++) {
                    if (clock.softExpired()) {
                        break;
                    }
                    int window = depth >= 4 ? 180 + depth * 12 : INFINITY;
                    int alpha = depth >= 4 ? Math.max(-INFINITY, lastScore - window) : -INFINITY;
                    int beta = depth >= 4 ? Math.min(INFINITY, lastScore + window) : INFINITY;
                    searchRoot(player, opponent, depth, alpha, beta, emptyCount);
                    if (iterationScore <= alpha || iterationScore >= beta) {
                        searchRoot(player, opponent, depth, -INFINITY, INFINITY, emptyCount);
                    }
                    bestMove = iterationMove;
                    bestScore = iterationScore;
                    lastScore = iterationScore;
                    completedDepth = depth;
                    if (clock.softExpired()) {
                        break;
                    }
                }
            }
        } catch (ZebraSearchClock.SearchTimeout ignored) {
        }

        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        return new ZebraSearchResult(bestMove, bestScore, completedDepth, clock.nodes(), elapsed,
                exact, outcomeSolved);
    }

    public ZebraSearchResult findBestMoveAtDepth(long player, long opponent, int depth) {
        long started = System.nanoTime();
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        if (legal == 0L) {
            return new ZebraSearchResult(-1, 0, 0, 0L, 0L, false, false);
        }
        table.beginSearch();
        clock.startUnlimited();
        int emptyCount = 64 - Long.bitCount(player | opponent);
        int effectiveDepth = Math.max(1, Math.min(depth, emptyCount));
        searchRoot(player, opponent, effectiveDepth, -INFINITY, INFINITY, emptyCount);
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        boolean solved = effectiveDepth == emptyCount;
        return new ZebraSearchResult(iterationMove, iterationScore, effectiveDepth, clock.nodes(), elapsed,
                solved, solved);
    }

    private void searchRoot(long player, long opponent, int depth, int alpha, int beta, int emptyCount) {
        clock.checkNow();
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        long key = ZebraBitBoard.hash(player, opponent);
        long entry = table.probe(key);
        int transpositionMove = ZebraTranspositionTable.move(entry);
        int moveCount = orderMoves(player, opponent, legal, transpositionMove, 0, emptyCount,
                depth == emptyCount, depth);
        int originalAlpha = alpha;
        int bestScore = -INFINITY;
        int bestMove = moveStack[0][0];
        boolean first = true;
        for (int index = 0; index < moveCount; index++) {
            clock.checkNow();
            int move = moveStack[0][index];
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            long nextPlayer = opponent ^ flipped;
            long nextOpponent = player | flipped | (1L << move);
            int score;
            if (first) {
                score = -search(nextPlayer, nextOpponent, depth - 1, -beta, -alpha, false, 1,
                        emptyCount - 1, true);
                first = false;
            } else {
                boolean reduced = reduceLateMove(depth, index, move, emptyCount, moveCount);
                if (reduced) {
                    score = -search(nextPlayer, nextOpponent, depth - 2, -alpha - 1, -alpha, false, 1,
                            emptyCount - 1, true);
                } else {
                    score = alpha + 1;
                }
                if (score > alpha) {
                    score = -search(nextPlayer, nextOpponent, depth - 1, -alpha - 1, -alpha, false, 1,
                            emptyCount - 1, true);
                }
                if (score > alpha && score < beta) {
                    score = -search(nextPlayer, nextOpponent, depth - 1, -beta, -alpha, false, 1,
                            emptyCount - 1, true);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
            }
            rootIterationScores[move] = score;
            if (alpha >= beta) {
                break;
            }
        }
        int bound = bestScore <= originalAlpha ? ZebraTranspositionTable.UPPER
                : bestScore >= beta ? ZebraTranspositionTable.LOWER : ZebraTranspositionTable.EXACT;
        table.store(key, depth, bestScore, bound, bestMove);
        long remaining = legal;
        while (remaining != 0L) {
            int move = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1L;
            rootScores[move] = rootIterationScores[move];
        }
        iterationMove = bestMove;
        iterationScore = bestScore;
    }

    private int search(long player, long opponent, int depth, int alpha, int beta, boolean passed, int ply,
                       int emptyCount, boolean allowProbCut) {
        clock.visit();
        if (depth <= 0 && emptyCount > 0 && !evaluatorRequiresLegalMoves) {
            return evaluator.evaluate(player, opponent, 0L);
        }
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        if (legal == 0L) {
            long opponentLegal = ZebraBitBoard.legalMoves(opponent, player);
            if (passed || opponentLegal == 0L) {
                return terminalScore(player, opponent);
            }
            return -search(opponent, player, depth, -beta, -alpha, true, ply, emptyCount, allowProbCut);
        }
        if (depth <= 0) {
            return evaluator.evaluate(player, opponent, legal);
        }

        if (depth == emptyCount && emptyCount <= profile.endgameTailEmpties()) {
            return ZebraEndgameTailSolver.solve(player, opponent, alpha, beta, passed, clock);
        }

        if (emptyCount == 1) {
            int move = Long.numberOfTrailingZeros(legal);
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            long nextPlayer = opponent ^ flipped;
            long nextOpponent = player | flipped | (1L << move);
            return -terminalScore(nextPlayer, nextOpponent);
        }

        long key = ZebraBitBoard.hash(player, opponent);
        long entry = table.probe(key);
        int transpositionMove = ZebraTranspositionTable.move(entry);
        if (entry != 0L && ZebraTranspositionTable.depth(entry) >= depth) {
            int tableScore = ZebraTranspositionTable.score(entry);
            int bound = ZebraTranspositionTable.bound(entry);
            if (bound == ZebraTranspositionTable.EXACT) {
                return tableScore;
            }
            if (bound == ZebraTranspositionTable.LOWER && tableScore >= beta) {
                return tableScore;
            }
            if (bound == ZebraTranspositionTable.UPPER && tableScore <= alpha) {
                return tableScore;
            }
        }

        int probCut = multiProbCut(player, opponent, depth, alpha, beta, passed, ply, emptyCount,
                allowProbCut, legal);
        if (probCut != NO_PROBCUT) {
            return probCut;
        }

        int originalAlpha = alpha;
        int moveCount = orderMoves(player, opponent, legal, transpositionMove, ply, emptyCount,
                depth == emptyCount, depth);
        int bestScore = -INFINITY;
        int bestMove = moveStack[ply][0];
        boolean first = true;
        for (int index = 0; index < moveCount; index++) {
            int move = moveStack[ply][index];
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            long nextPlayer = opponent ^ flipped;
            long nextOpponent = player | flipped | (1L << move);
            int score;
            if (first) {
                score = -search(nextPlayer, nextOpponent, depth - 1, -beta, -alpha, false, ply + 1,
                        emptyCount - 1, allowProbCut);
                first = false;
            } else {
                boolean reduced = reduceLateMove(depth, index, move, emptyCount, moveCount);
                if (reduced) {
                    score = -search(nextPlayer, nextOpponent, depth - 2, -alpha - 1, -alpha, false, ply + 1,
                            emptyCount - 1, allowProbCut);
                } else {
                    score = alpha + 1;
                }
                if (score > alpha) {
                    score = -search(nextPlayer, nextOpponent, depth - 1, -alpha - 1, -alpha, false, ply + 1,
                            emptyCount - 1, allowProbCut);
                }
                if (score > alpha && score < beta) {
                    score = -search(nextPlayer, nextOpponent, depth - 1, -beta, -alpha, false, ply + 1,
                            emptyCount - 1, allowProbCut);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                history[move] = Math.min(50_000, history[move] + depth * depth);
                break;
            }
        }
        int bound = bestScore <= originalAlpha ? ZebraTranspositionTable.UPPER
                : bestScore >= beta ? ZebraTranspositionTable.LOWER : ZebraTranspositionTable.EXACT;
        table.store(key, depth, bestScore, bound, bestMove);
        return bestScore;
    }

    private int multiProbCut(long player, long opponent, int depth, int alpha, int beta, boolean passed,
                             int ply, int emptyCount, boolean allowed, long legal) {
        if (!allowed || depth < profile.probCutMinimumDepth()
                || emptyCount <= profile.exactAttemptEmpties() || beta - alpha != 1
                || Long.bitCount(legal) < 3) {
            return NO_PROBCUT;
        }
        int shallowDepth = Math.min(depth - 2, profile.probCutShallowDepth(depth));
        int margin = profile.probCutMargin(depth);
        if (beta < 18_000) {
            int threshold = Math.min(20_000, beta + margin);
            int shallow = search(player, opponent, shallowDepth, threshold - 1, threshold, passed, ply,
                    emptyCount, false);
            if (shallow >= threshold) {
                return beta;
            }
        }
        if (alpha > -18_000) {
            int threshold = Math.max(-20_000, alpha - margin);
            int shallow = search(player, opponent, shallowDepth, threshold, threshold + 1, passed, ply,
                    emptyCount, false);
            if (shallow <= threshold) {
                return alpha;
            }
        }
        return NO_PROBCUT;
    }

    private int orderMoves(long player, long opponent, long legal, int transpositionMove, int ply,
                           int emptyCount, boolean exactSearch, int searchDepth) {
        int[] moves = moveStack[ply];
        int[] scores = scoreStack[ply];
        long oddRegions = exactSearch ? oddRegionMask(~(player | opponent)) : 0L;
        int count = 0;
        long remaining = legal;
        while (remaining != 0L) {
            int move = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1L;
            int score = SQUARE_ORDER[move] + history[move];
            if (move == transpositionMove) {
                score += 1_000_000;
            }
            if (ply == 0 && move == rootPolicyMove) {
                score += 500_000;
            }
            if (ply == 0 && move == rootHintMove) {
                score += 750_000;
            }
            if (((1L << move) & oddRegions) != 0L) {
                score += 7_000;
            }
            if (ply == 0) {
                score += rootScores[move];
            }
            if (exactSearch || searchDepth >= 3) {
                long flipped = ZebraBitBoard.flips(player, opponent, move);
                long nextPlayer = opponent ^ flipped;
                long nextOpponent = player | flipped | (1L << move);
                int opponentMobility = Long.bitCount(ZebraBitBoard.legalMoves(nextPlayer, nextOpponent));
                score -= opponentMobility * (exactSearch ? 320 : 105);
                score -= Long.bitCount(flipped) * (emptyCount > 24 ? 8 : 0);
            }
            int insertion = count;
            while (insertion > 0 && scores[insertion - 1] < score) {
                scores[insertion] = scores[insertion - 1];
                moves[insertion] = moves[insertion - 1];
                insertion--;
            }
            scores[insertion] = score;
            moves[insertion] = move;
            count++;
        }
        return count;
    }

    private int firstOrderedMove(long player, long opponent, long legal, int emptyCount) {
        orderMoves(player, opponent, legal, -1, 0, emptyCount, false, 1);
        return moveStack[0][0];
    }

    private boolean reduceLateMove(int depth, int moveIndex, int move, int emptyCount, int moveCount) {
        return profile.lateMoveReduction() && depth >= 7 && moveIndex >= 5 && moveCount >= 7
                && emptyCount > profile.exactAttemptEmpties() && ((1L << move) & ZebraBitBoard.CORNERS) == 0L;
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

    private static long oddRegionMask(long empty) {
        long remaining = empty;
        long odd = 0L;
        while (remaining != 0L) {
            long region = Long.lowestOneBit(remaining);
            long previous;
            do {
                previous = region;
                region |= ZebraBitBoard.orthogonalAdjacent(region) & empty;
            } while (region != previous);
            if ((Long.bitCount(region) & 1) == 1) {
                odd |= region;
            }
            remaining &= ~region;
        }
        return odd;
    }

    private void decayHistory() {
        for (int i = 0; i < history.length; i++) {
            history[i] >>>= 2;
        }
    }
}
