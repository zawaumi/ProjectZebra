package com.zawa.client.util.ai;

import com.zawa.client.util.TimeManager;
import com.zawa.client.util.table.BitBoardUtils;
import com.zawa.client.util.table.TranspositionTable;

public class ExactSearcher {
    private final TranspositionTable transpositionTable;
    private final TimeManager timeManager;

    public ExactSearcher(TranspositionTable transpositionTable, TimeManager timeManager) {
        this.transpositionTable = transpositionTable;
        this.timeManager = timeManager;
    }

    public double search(long myBoard, long opponentBoard, double alphaValue, double betaValue, int emptyCount, boolean isPassed) {
        timeManager.checkTime();

        if (emptyCount == 0) {
            int pieceDifference = Long.bitCount(myBoard) - Long.bitCount(opponentBoard);
            return pieceDifference > 0 ? 1000.0 + pieceDifference : (pieceDifference < 0 ? -1000.0 + pieceDifference : 0.0);
        }

        long legalMoves = BitBoardUtils.getLegalMoves(myBoard, opponentBoard);
        if (legalMoves == 0L) {
            if (isPassed) {
                int pieceDifference = Long.bitCount(myBoard) - Long.bitCount(opponentBoard);
                if (pieceDifference > 0) return 1000.0 + pieceDifference + emptyCount;
                if (pieceDifference < 0) return -1000.0 + pieceDifference - emptyCount;
                return 0.0;
            }
            return -search(opponentBoard, myBoard, -betaValue, -alphaValue, emptyCount, true);
        }

        if (emptyCount == 1) {
            int moveIndex = Long.numberOfTrailingZeros(legalMoves);
            long flippedBoard = BitBoardUtils.getFlip(myBoard, opponentBoard, moveIndex);
            int pieceDifference = Long.bitCount(myBoard | (1L << moveIndex) | flippedBoard) * 2 - 64;
            return pieceDifference > 0 ? 1000.0 + pieceDifference : (pieceDifference < 0 ? -1000.0 + pieceDifference : 0.0);
        }

        double bestScore = -Double.MAX_VALUE;
        boolean isFirstMove = true;
        long boardHash = ZobristHasher.computeHash(myBoard, opponentBoard, !isPassed);
        int transpositionTableMove = transpositionTable.getMove(boardHash);

        long[] sortedMoves = MoveOrderer.getSortedMoves(legalMoves, transpositionTableMove, myBoard, opponentBoard);

        for (int i = 0; i < sortedMoves.length; i++) {
            int moveIndex = (int) (sortedMoves[i] & 0xFFFFFFFFL);
            long flippedBoard = BitBoardUtils.getFlip(myBoard, opponentBoard, moveIndex);
            long nextMyBoard = opponentBoard ^ flippedBoard;
            long nextOpponentBoard = myBoard | (1L << moveIndex) | flippedBoard;
            double currentScore;
            
            if (isFirstMove) {
                currentScore = -search(nextMyBoard, nextOpponentBoard, -betaValue, -alphaValue, emptyCount - 1, false);
                isFirstMove = false;
            } else {
                currentScore = -search(nextMyBoard, nextOpponentBoard, -alphaValue - 0.0001, -alphaValue, emptyCount - 1, false);
                if (alphaValue < currentScore && currentScore < betaValue) {
                    currentScore = -search(nextMyBoard, nextOpponentBoard, -betaValue, -currentScore, emptyCount - 1, false);
                }
            }
            if (currentScore > bestScore) {
                bestScore = currentScore;
            }
            alphaValue = Math.max(alphaValue, bestScore);
            if (alphaValue >= betaValue) {
                break;
            }
        }
        return bestScore;
    }
}