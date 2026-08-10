package com.zawa.client.util.ai;

import com.zawa.client.util.table.BitBoardUtils;

public class MoveOrderer {

    public static long[] getSortedMoves(long legalMoves, int transpositionTableMove, long myBoard, long opponentBoard) {
        int movesCount = Long.bitCount(legalMoves);
        long[] sortedMoves = new long[movesCount];
        int moveIndex = 0;
        long remainingMoves = legalMoves;
        
        while (remainingMoves != 0L) {
            int currentMove = Long.numberOfTrailingZeros(remainingMoves);
            remainingMoves &= remainingMoves - 1L;
            
            long flippedBoard = BitBoardUtils.getFlip(myBoard, opponentBoard, currentMove);
            long nextMyBoard = opponentBoard ^ flippedBoard;
            long nextOpponentBoard = myBoard | (1L << currentMove) | flippedBoard;
            
            double evaluationScore = OthelloEvaluator.evaluate(nextMyBoard, nextOpponentBoard);
            int moveScore = (int) -evaluationScore;
            
            if (currentMove == transpositionTableMove) {
                moveScore += 10000;
            }
            sortedMoves[moveIndex++] = ((long) moveScore << 32) | currentMove;
        }
        
        for (int i = 0; i < movesCount - 1; i++) {
            for (int j = i + 1; j < movesCount; j++) {
                if (sortedMoves[i] < sortedMoves[j]) {
                    long tempMove = sortedMoves[i];
                    sortedMoves[i] = sortedMoves[j];
                    sortedMoves[j] = tempMove;
                }
            }
        }
        return sortedMoves;
    }
}