package com.zawa.client.util.table;

public class BitBoardUtils {
    public static final long MASK_CORNER = 0x8100000000000081L;
    public static final long MASK_X = 0x4281000000008142L & ~0x8100000000000081L;
    public static final long MASK_C = 0x2400810000810024L & ~0x8100000000000081L;
    public static final long MASK_EDGE = 0xFF818181818181FFL & ~(MASK_CORNER | MASK_X | MASK_C);
    public static final long MASK_INNER = ~(MASK_CORNER | MASK_X | MASK_C | MASK_EDGE);

    public static long getLegalMoves(long myBoard, long opponentBoard) {
        long emptyBoard = ~(myBoard | opponentBoard);
        long legalMoves = 0L;
        
        long horizontalMask = opponentBoard & 0x7E7E7E7E7E7E7E7EL;
        long tempBoard = horizontalMask & (myBoard >>> 1);
        tempBoard |= horizontalMask & (tempBoard >>> 1); tempBoard |= horizontalMask & (tempBoard >>> 1); tempBoard |= horizontalMask & (tempBoard >>> 1); tempBoard |= horizontalMask & (tempBoard >>> 1); tempBoard |= horizontalMask & (tempBoard >>> 1);
        legalMoves |= emptyBoard & (tempBoard >>> 1);
        
        tempBoard = horizontalMask & (myBoard << 1);
        tempBoard |= horizontalMask & (tempBoard << 1); tempBoard |= horizontalMask & (tempBoard << 1); tempBoard |= horizontalMask & (tempBoard << 1); tempBoard |= horizontalMask & (tempBoard << 1); tempBoard |= horizontalMask & (tempBoard << 1);
        legalMoves |= emptyBoard & (tempBoard << 1);
        
        long verticalMask = opponentBoard & 0x00FFFFFFFFFFFF00L;
        tempBoard = verticalMask & (myBoard >>> 8);
        tempBoard |= verticalMask & (tempBoard >>> 8); tempBoard |= verticalMask & (tempBoard >>> 8); tempBoard |= verticalMask & (tempBoard >>> 8); tempBoard |= verticalMask & (tempBoard >>> 8); tempBoard |= verticalMask & (tempBoard >>> 8);
        legalMoves |= emptyBoard & (tempBoard >>> 8);
        
        tempBoard = verticalMask & (myBoard << 8);
        tempBoard |= verticalMask & (tempBoard << 8); tempBoard |= verticalMask & (tempBoard << 8); tempBoard |= verticalMask & (tempBoard << 8); tempBoard |= verticalMask & (tempBoard << 8); tempBoard |= verticalMask & (tempBoard << 8);
        legalMoves |= emptyBoard & (tempBoard << 8);
        
        long diagonalMask = opponentBoard & 0x007E7E7E7E7E7E00L;
        tempBoard = diagonalMask & (myBoard >>> 7);
        tempBoard |= diagonalMask & (tempBoard >>> 7); tempBoard |= diagonalMask & (tempBoard >>> 7); tempBoard |= diagonalMask & (tempBoard >>> 7); tempBoard |= diagonalMask & (tempBoard >>> 7); tempBoard |= diagonalMask & (tempBoard >>> 7);
        legalMoves |= emptyBoard & (tempBoard >>> 7);
        
        tempBoard = diagonalMask & (myBoard << 7);
        tempBoard |= diagonalMask & (tempBoard << 7); tempBoard |= diagonalMask & (tempBoard << 7); tempBoard |= diagonalMask & (tempBoard << 7); tempBoard |= diagonalMask & (tempBoard << 7); tempBoard |= diagonalMask & (tempBoard << 7);
        legalMoves |= emptyBoard & (tempBoard << 7);
        
        tempBoard = diagonalMask & (myBoard >>> 9);
        tempBoard |= diagonalMask & (tempBoard >>> 9); tempBoard |= diagonalMask & (tempBoard >>> 9); tempBoard |= diagonalMask & (tempBoard >>> 9); tempBoard |= diagonalMask & (tempBoard >>> 9); tempBoard |= diagonalMask & (tempBoard >>> 9);
        legalMoves |= emptyBoard & (tempBoard >>> 9);
        
        tempBoard = diagonalMask & (myBoard << 9);
        tempBoard |= diagonalMask & (tempBoard << 9); tempBoard |= diagonalMask & (tempBoard << 9); tempBoard |= diagonalMask & (tempBoard << 9); tempBoard |= diagonalMask & (tempBoard << 9); tempBoard |= diagonalMask & (tempBoard << 9);
        legalMoves |= emptyBoard & (tempBoard << 9);
        
        return legalMoves;
    }

    public static long getFlip(long myBoard, long opponentBoard, int moveIndex) {
        long flippedBoard = 0L;
        long movePosition = 1L << moveIndex;
        
        long horizontalMask = opponentBoard & 0x7E7E7E7E7E7E7E7EL;
        long outBoard = horizontalMask & (movePosition >>> 1);
        outBoard |= horizontalMask & (outBoard >>> 1); outBoard |= horizontalMask & (outBoard >>> 1); outBoard |= horizontalMask & (outBoard >>> 1); outBoard |= horizontalMask & (outBoard >>> 1); outBoard |= horizontalMask & (outBoard >>> 1);
        if ((myBoard & (outBoard >>> 1)) != 0) flippedBoard |= outBoard;
        
        outBoard = horizontalMask & (movePosition << 1);
        outBoard |= horizontalMask & (outBoard << 1); outBoard |= horizontalMask & (outBoard << 1); outBoard |= horizontalMask & (outBoard << 1); outBoard |= horizontalMask & (outBoard << 1); outBoard |= horizontalMask & (outBoard << 1);
        if ((myBoard & (outBoard << 1)) != 0) flippedBoard |= outBoard;
        
        long verticalMask = opponentBoard & 0x00FFFFFFFFFFFF00L;
        outBoard = verticalMask & (movePosition >>> 8);
        outBoard |= verticalMask & (outBoard >>> 8); outBoard |= verticalMask & (outBoard >>> 8); outBoard |= verticalMask & (outBoard >>> 8); outBoard |= verticalMask & (outBoard >>> 8); outBoard |= verticalMask & (outBoard >>> 8);
        if ((myBoard & (outBoard >>> 8)) != 0) flippedBoard |= outBoard;
        
        outBoard = verticalMask & (movePosition << 8);
        outBoard |= verticalMask & (outBoard << 8); outBoard |= verticalMask & (outBoard << 8); outBoard |= verticalMask & (outBoard << 8); outBoard |= verticalMask & (outBoard << 8); outBoard |= verticalMask & (outBoard << 8);
        if ((myBoard & (outBoard << 8)) != 0) flippedBoard |= outBoard;
        
        long diagonalMask = opponentBoard & 0x007E7E7E7E7E7E00L;
        outBoard = diagonalMask & (movePosition >>> 9);
        outBoard |= diagonalMask & (outBoard >>> 9); outBoard |= diagonalMask & (outBoard >>> 9); outBoard |= diagonalMask & (outBoard >>> 9); outBoard |= diagonalMask & (outBoard >>> 9); outBoard |= diagonalMask & (outBoard >>> 9);
        if ((myBoard & (outBoard >>> 9)) != 0) flippedBoard |= outBoard;
        
        outBoard = diagonalMask & (movePosition << 9);
        outBoard |= diagonalMask & (outBoard << 9); outBoard |= diagonalMask & (outBoard << 9); outBoard |= diagonalMask & (outBoard << 9); outBoard |= diagonalMask & (outBoard << 9); outBoard |= diagonalMask & (outBoard << 9);
        if ((myBoard & (outBoard << 9)) != 0) flippedBoard |= outBoard;
        
        outBoard = diagonalMask & (movePosition >>> 7);
        outBoard |= diagonalMask & (outBoard >>> 7); outBoard |= diagonalMask & (outBoard >>> 7); outBoard |= diagonalMask & (outBoard >>> 7); outBoard |= diagonalMask & (outBoard >>> 7); outBoard |= diagonalMask & (outBoard >>> 7);
        if ((myBoard & (outBoard >>> 7)) != 0) flippedBoard |= outBoard;
        
        outBoard = diagonalMask & (movePosition << 7);
        outBoard |= diagonalMask & (outBoard << 7); outBoard |= diagonalMask & (outBoard << 7); outBoard |= diagonalMask & (outBoard << 7); outBoard |= diagonalMask & (outBoard << 7); outBoard |= diagonalMask & (outBoard << 7);
        if ((myBoard & (outBoard << 7)) != 0) flippedBoard |= outBoard;
        
        return flippedBoard;
    }

    public static long getFrontier(long currentBoard, long emptyBoard) {
        long frontierBoard = 0L;
        frontierBoard |= (currentBoard << 1) & emptyBoard & 0xFEFEFEFEFEFEFEFEL;
        frontierBoard |= (currentBoard >>> 1) & emptyBoard & 0x7F7F7F7F7F7F7F7FL;
        frontierBoard |= (currentBoard << 8) & emptyBoard;
        frontierBoard |= (currentBoard >>> 8) & emptyBoard;
        frontierBoard |= (currentBoard << 7) & emptyBoard & 0x7F7F7F7F7F7F7F7FL;
        frontierBoard |= (currentBoard >>> 7) & emptyBoard & 0xFEFEFEFEFEFEFEFEL;
        frontierBoard |= (currentBoard << 9) & emptyBoard & 0xFEFEFEFEFEFEFEFEL;
        frontierBoard |= (currentBoard >>> 9) & emptyBoard & 0x7F7F7F7F7F7F7F7FL;
        return frontierBoard;
    }
}