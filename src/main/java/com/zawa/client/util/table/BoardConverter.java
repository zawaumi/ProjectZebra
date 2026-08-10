package com.zawa.client.util.table;

public class BoardConverter {

    public static long[] convertToBitBoards(Integer[][] othelloArray, Integer myTurn) {
        long myBoard = 0L;
        long opponentBoard = 0L;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (othelloArray[row][col] != null) {
                    long bitPosition = 1L << (row * 8 + col);
                    if (othelloArray[row][col].equals(myTurn)) {
                        myBoard |= bitPosition;
                    } else if (othelloArray[row][col].equals(-myTurn)) {
                        opponentBoard |= bitPosition;
                    }
                }
            }
        }
        return new long[]{myBoard, opponentBoard};
    }
}