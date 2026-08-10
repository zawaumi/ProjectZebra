package com.zawa.client.network;

final class OthelloMatchRecorder {
    private final StringBuilder moves = new StringBuilder(128);
    private Integer[][] previous;
    private int myTurn;

    void start(int turn) {
        moves.setLength(0);
        previous = null;
        myTurn = turn;
    }

    void board(Integer[][] board) {
        if (previous != null) {
            int addedRow = -1;
            int addedColumn = -1;
            for (int row = 0; row < 8; row++) {
                for (int column = 0; column < 8; column++) {
                    int before = value(previous[row][column]);
                    int after = value(board[row][column]);
                    if (before == 0 && after != 0) {
                        addedRow = row;
                        addedColumn = column;
                    }
                }
            }
            if (addedRow >= 0) {
                moves.append((char) ('A' + addedColumn)).append(addedRow + 1);
            }
        }
        previous = copy(board);
    }

    void finish(String result, String aiName) {
        String color = myTurn == 1 ? "black" : myTurn == -1 ? "white" : "unknown";
        System.out.printf("ZEBRA_MATCH ai=%s color=%s moves=%s result=%s%n",
                aiName, color, moves, result);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static Integer[][] copy(Integer[][] board) {
        Integer[][] result = new Integer[8][8];
        for (int row = 0; row < 8; row++) {
            System.arraycopy(board[row], 0, result[row], 0, 8);
        }
        return result;
    }
}
