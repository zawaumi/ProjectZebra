package com.zawa.client.util.ai;

import com.zawa.client.util.TimeManager;
import com.zawa.client.util.table.BitBoardUtils;
import com.zawa.client.util.table.TranspositionTable;

public class OthelloSearcher {

    private static final int ENDGAME_START_EMPTY_COUNT = 20;

    private final TranspositionTable transpositionTable;
    private final TimeManager timeManager;
    private final NegaScoutSearcher negaScoutSearcher;
    private final ExactSearcher exactSearcher;

    public OthelloSearcher(TranspositionTable transpositionTable, TimeManager timeManager) {
        this.transpositionTable = transpositionTable;
        this.timeManager = timeManager;
        this.negaScoutSearcher = new NegaScoutSearcher(transpositionTable, timeManager);
        this.exactSearcher = new ExactSearcher(transpositionTable, timeManager);
    }

    public int searchBestMove(long myBoard, long opponentBoard, long legalMoves, int emptyCount) {
        int bestMoveIndex = Long.numberOfTrailingZeros(legalMoves);
        int maxSearchDepth = emptyCount <= ENDGAME_START_EMPTY_COUNT ? emptyCount : 60;

        try {
            for (int searchDepth = 1; searchDepth <= maxSearchDepth; searchDepth++) {
                int currentBestMove = searchRoot(myBoard, opponentBoard, legalMoves, searchDepth, emptyCount);
                if (currentBestMove != -1) {
                    bestMoveIndex = currentBestMove;
                }
                if (emptyCount <= ENDGAME_START_EMPTY_COUNT && searchDepth == emptyCount) {
                    break;
                }
            }
        } catch (TimeManager.TimeOutException ignored) {
        }

        return bestMoveIndex;
    }

    private int searchRoot(long myBoard, long opponentBoard, long legalMoves, int searchDepth, int emptyCount) {
        double alphaValue = -Double.MAX_VALUE;
        double betaValue = Double.MAX_VALUE;
        int bestMoveIndex = -1;
        double bestScore = -Double.MAX_VALUE;

        long boardHash = ZobristHasher.computeHash(myBoard, opponentBoard, true);
        int transpositionTableMove = transpositionTable.getMove(boardHash);

        long[] sortedMoves = MoveOrderer.getSortedMoves(legalMoves, transpositionTableMove, myBoard, opponentBoard);

        for (int i = 0; i < sortedMoves.length; i++) {
            int moveIndex = (int) (sortedMoves[i] & 0xFFFFFFFFL);
            long flippedBoard = BitBoardUtils.getFlip(myBoard, opponentBoard, moveIndex);
            long nextMyBoard = opponentBoard ^ flippedBoard;
            long nextOpponentBoard = myBoard | (1L << moveIndex) | flippedBoard;
            double currentScore;

            if (emptyCount <= ENDGAME_START_EMPTY_COUNT && searchDepth == emptyCount) {
                if (bestMoveIndex == -1) {
                    currentScore = -exactSearcher.search(nextMyBoard, nextOpponentBoard, -betaValue, -alphaValue, emptyCount - 1, false);
                } else {
                    currentScore = -exactSearcher.search(nextMyBoard, nextOpponentBoard, -alphaValue - 0.0001, -alphaValue, emptyCount - 1, false);
                    if (alphaValue < currentScore && currentScore < betaValue) {
                        currentScore = -exactSearcher.search(nextMyBoard, nextOpponentBoard, -betaValue, -currentScore, emptyCount - 1, false);
                    }
                }
            } else {
                if (bestMoveIndex == -1) {
                    currentScore = -negaScoutSearcher.search(nextMyBoard, nextOpponentBoard, searchDepth - 1, -betaValue, -alphaValue, emptyCount - 1, false);
                } else {
                    currentScore = -negaScoutSearcher.search(nextMyBoard, nextOpponentBoard, searchDepth - 1, -alphaValue - 0.0001, -alphaValue, emptyCount - 1, false);
                    if (alphaValue < currentScore && currentScore < betaValue) {
                        currentScore = -negaScoutSearcher.search(nextMyBoard, nextOpponentBoard, searchDepth - 1, -betaValue, -currentScore, emptyCount - 1, false);
                    }
                }
            }

            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestMoveIndex = moveIndex;
            }
            alphaValue = Math.max(alphaValue, bestScore);
        }

        if (bestMoveIndex != -1) {
            transpositionTable.store(boardHash, searchDepth, bestScore, 0, bestMoveIndex);
        }
        return bestMoveIndex;
    }
}