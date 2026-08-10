package com.zawa.client.util.ai;

import com.zawa.client.util.TimeManager;
import com.zawa.client.util.table.BitBoardUtils;
import com.zawa.client.util.table.TranspositionTable;

public class NegaScoutSearcher {
    private final TranspositionTable transpositionTable;
    private final TimeManager timeManager;

    public NegaScoutSearcher(TranspositionTable transpositionTable, TimeManager timeManager) {
        this.transpositionTable = transpositionTable;
        this.timeManager = timeManager;
    }

    public double search(long myBoard, long opponentBoard, int searchDepth, double alphaValue, double betaValue, int emptyCount, boolean isPassed) {
        timeManager.checkTime();

        if (searchDepth == 0 || emptyCount == 0) {
            return OthelloEvaluator.evaluate(myBoard, opponentBoard);
        }

        long boardHash = ZobristHasher.computeHash(myBoard, opponentBoard, !isPassed);
        double originalAlpha = alphaValue;
        double transpositionTableValue = transpositionTable.getScore(boardHash, searchDepth);
        
        if (transpositionTableValue != -Double.MAX_VALUE) {
            int transpositionTableFlag = transpositionTable.getFlag(boardHash);
            if (transpositionTableFlag == 0) return transpositionTableValue;
            if (transpositionTableFlag == 1 && transpositionTableValue <= alphaValue) return alphaValue;
            if (transpositionTableFlag == 2 && transpositionTableValue >= betaValue) return betaValue;
        }

        long legalMoves = BitBoardUtils.getLegalMoves(myBoard, opponentBoard);
        if (legalMoves == 0L) {
            if (isPassed) {
                int pieceDifference = Long.bitCount(myBoard) - Long.bitCount(opponentBoard);
                return pieceDifference > 0 ? 1000.0 + pieceDifference : (pieceDifference < 0 ? -1000.0 + pieceDifference : 0.0);
            }
            return -search(opponentBoard, myBoard, searchDepth, -betaValue, -alphaValue, emptyCount, true);
        }

        double bestScore = -Double.MAX_VALUE;
        int bestMoveIndex = -1;
        boolean isFirstMove = true;
        int transpositionTableMove = transpositionTable.getMove(boardHash);

        long[] sortedMoves = MoveOrderer.getSortedMoves(legalMoves, transpositionTableMove, myBoard, opponentBoard);

        for (int i = 0; i < sortedMoves.length; i++) {
            int moveIndex = (int) (sortedMoves[i] & 0xFFFFFFFFL);
            long flippedBoard = BitBoardUtils.getFlip(myBoard, opponentBoard, moveIndex);
            long nextMyBoard = opponentBoard ^ flippedBoard;
            long nextOpponentBoard = myBoard | (1L << moveIndex) | flippedBoard;
            double currentScore;
            
            if (isFirstMove) {
                currentScore = -search(nextMyBoard, nextOpponentBoard, searchDepth - 1, -betaValue, -alphaValue, emptyCount - 1, false);
                isFirstMove = false;
            } else {
                currentScore = -search(nextMyBoard, nextOpponentBoard, searchDepth - 1, -alphaValue - 0.0001, -alphaValue, emptyCount - 1, false);
                if (alphaValue < currentScore && currentScore < betaValue) {
                    currentScore = -search(nextMyBoard, nextOpponentBoard, searchDepth - 1, -betaValue, -currentScore, emptyCount - 1, false);
                }
            }
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestMoveIndex = moveIndex;
            }
            alphaValue = Math.max(alphaValue, bestScore);
            if (alphaValue >= betaValue) {
                break;
            }
        }

        int scoreFlag = 0;
        if (bestScore <= originalAlpha) scoreFlag = 1;
        else if (bestScore >= betaValue) scoreFlag = 2;
        transpositionTable.store(boardHash, searchDepth, bestScore, scoreFlag, bestMoveIndex);

        return bestScore;
    }
}