package com.zawa.client.ai.research;

import com.zawa.client.ai.AbstractClientAi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClientMonteAI extends AbstractClientAi {
    private final int PLAYOUT_TIME_MS = 1000;
    private final Random random = new Random();

    public ClientMonteAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
    }

    public ClientMonteAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "MonteAI";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        List<Integer[]> validMoves = new ArrayList<>();
        for (int r = 0; r < othello_array.length; r++) {
            for (int c = 0; c < othello_array[r].length; c++) {
                if (isValidMove(othello_array, myturn, r, c)) {
                    validMoves.add(new Integer[]{r, c});
                }
            }
        }

        if (validMoves.isEmpty()) {
            return null;
        }

        if (validMoves.size() == 1) {
            return validMoves.get(0);
        }

        int[][] wins = new int[8][8];
        int[][] plays = new int[8][8];
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < PLAYOUT_TIME_MS) {
            int[][] simBoard = new int[8][8];
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    simBoard[r][c] = (othello_array[r][c] == null) ? 0 : othello_array[r][c];
                }
            }
            
            Integer[] firstMove = validMoves.get(random.nextInt(validMoves.size()));
            int r = firstMove[0];
            int c = firstMove[1];

            int result = playOut(r, c, myturn, simBoard);

            plays[r][c]++;
            if (result == myturn) {
                wins[r][c]++;
            }
        }

        Integer[] bestMove = null;
        double bestRate = -1.0;

        for (Integer[] move : validMoves) {
            int r = move[0];
            int c = move[1];
            if (plays[r][c] > 0) {
                double rate = (double) wins[r][c] / plays[r][c];
                if (rate > bestRate) {
                    bestRate = rate;
                    bestMove = move;
                }
            }
        }

        return bestMove != null ? bestMove : validMoves.get(0);
    }

    private int playOut(int r, int c, int turn, int[][] board) {
        putPieceSim(board, r, c, turn);
        int currentTurn = -turn;

        while (true) {
            List<int[]> moves = getValidMovesSim(board, currentTurn);

            if (moves.isEmpty()) {
                currentTurn = -currentTurn;
                moves = getValidMovesSim(board, currentTurn);

                if (moves.isEmpty()) {
                    return getWinner(board);
                }
            }

            int[] nextMove = moves.get(random.nextInt(moves.size()));
            putPieceSim(board, nextMove[0], nextMove[1], currentTurn);

            currentTurn = -currentTurn;
        }
    }

    private int getWinner(int[][] board) {
        int black = 0;
        int white = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == 1) black++;
                else if (board[r][c] == -1) white++;
            }
        }
        if (black > white) return 1;
        if (white > black) return -1;
        return 0;
    }

    private List<int[]> getValidMovesSim(int[][] board, int turn) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isValidMoveSim(board, turn, r, c)) {
                    moves.add(new int[]{r, c});
                }
            }
        }
        return moves;
    }

    private boolean isValidMoveSim(int[][] board, int turn, int r, int c) {
        if (board[r][c] != 0) return false;

        int[] dirs = {-1, 0, 1};
        for (int dr : dirs) {
            for (int dc : dirs) {
                if (dr == 0 && dc == 0) continue;
                if (checkDirectionSim(board, turn, r, c, dr, dc)) return true;
            }
        }
        return false;
    }

    private boolean checkDirectionSim(int[][] board, int turn, int r, int c, int dr, int dc) {
        int nr = r + dr;
        int nc = c + dc;
        boolean hasOpponent = false;

        while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
            if (board[nr][nc] == -turn) {
                hasOpponent = true;
            } else if (board[nr][nc] == turn) {
                return hasOpponent;
            } else {
                break;
            }
            nr += dr;
            nc += dc;
        }
        return false;
    }

    private void putPieceSim(int[][] board, int r, int c, int turn) {
        board[r][c] = turn;
        int[] dirs = {-1, 0, 1};

        for (int dr : dirs) {
            for (int dc : dirs) {
                if (dr == 0 && dc == 0) continue;
                if (checkDirectionSim(board, turn, r, c, dr, dc)) {
                    int nr = r + dr;
                    int nc = c + dc;
                    while (board[nr][nc] == -turn) {
                        board[nr][nc] = turn;
                        nr += dr;
                        nc += dc;
                    }
                }
            }
        }
    }
}