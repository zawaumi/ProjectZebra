package com.zawa.client.ai;

public abstract class AbstractClientAi implements ClientAiInterface {
    protected Integer myturn;
    protected Integer[][] othello_array;
    protected String ai_name;

    public AbstractClientAi(Integer myturn, Integer[][] othello_array) {
        this.myturn = myturn;
        this.othello_array = othello_array;
    }

    public String getAiName() {
        return ai_name;
    }

    public abstract Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn);

    protected boolean isValidMove(Integer[][] othello_array, Integer myturn, int row, int col) {
        if (othello_array[row][col] != null && othello_array[row][col] != 0) {
            return false;
        }

        int[] directions = {-1, 0, 1};
        for (int dr : directions) {
            for (int dc : directions) {
                if (dr == 0 && dc == 0) continue;
                if (checkDirection(othello_array, myturn, row, col, dr, dc)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean checkDirection(Integer[][] othello_array, Integer myturn, int row, int col, int dr, int dc) {
        int r = row + dr;
        int c = col + dc;
        boolean hasOpponentPiece = false;

        while (r >= 0 && r < othello_array.length && c >= 0 && c < othello_array[r].length) {
            if (othello_array[r][c] != null && othello_array[r][c] == -myturn) {
                hasOpponentPiece = true;
            } else if (othello_array[r][c] != null && othello_array[r][c] == myturn) {
                return hasOpponentPiece;
            } else {
                break;
            }
            r += dr;
            c += dc;
        }
        return false;
    }
}