package com.zawa.client.ai.ab;

import com.zawa.client.ai.AbstractClientAi;

import java.util.ArrayList;
import java.util.List;

public class ClientAlphaBetaAI extends AbstractClientAi {

    private static final int SEARCH_DEPTH = 6;
    private static final int MOBILITY_WEIGHT = 10;

    private static final int[][] BASE_EVAL_MATRIX = {
        { 12000, -3000, 1000,  800,  800, 1000, -3000, 12000},
        { -3000, -5000, -450, -500, -500, -450, -5000, -3000},
        {  1000,  -450,   30,   10,   10,   30,  -450,  1000},
        {   800,  -500,   10,   50,   50,   10,  -500,   800},
        {   800,  -500,   10,   50,   50,   10,  -500,   800},
        {  1000,  -450,   30,   10,   10,   30,  -450,  1000},
        { -3000, -5000, -450, -500, -500, -450, -5000, -3000},
        { 12000, -3000, 1000,  800,  800, 1000, -3000, 12000}
    };

    public ClientAlphaBetaAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
    }

    public ClientAlphaBetaAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "AlphaBetaAI";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        int[][] board = convertBoard(othello_array);
        List<int[]> validMoves = getValidMoves(board, myturn);

        if (validMoves.isEmpty()) {
            return null;
        }

        if (validMoves.size() == 1) {
            return new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};
        }

        int[] bestMove = null;
        int maxEval = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (int[] move : validMoves) {
            int[][] nextBoard = simulateMove(board, move[0], move[1], myturn);
            int eval = alphaBetaSearch(nextBoard, SEARCH_DEPTH - 1, alpha, beta, -myturn, myturn);
            
            if (eval > maxEval) {
                maxEval = eval;
                bestMove = move;
            }
            alpha = Math.max(alpha, eval);
        }

        return bestMove != null ? new Integer[]{bestMove[0], bestMove[1]} : new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};
    }

    private int alphaBetaSearch(int[][] board, int depth, int alpha, int beta, int currentTurn, int myTurn) {
        if (depth == 0 || isGameOver(board)) {
            return evaluateBoard(board, myTurn);
        }

        List<int[]> moves = getValidMoves(board, currentTurn);

        if (moves.isEmpty()) {
            return alphaBetaSearch(board, depth - 1, alpha, beta, -currentTurn, myTurn);
        }

        if (currentTurn == myTurn) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : moves) {
                int[][] nextBoard = simulateMove(board, move[0], move[1], currentTurn);
                int eval = alphaBetaSearch(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : moves) {
                int[][] nextBoard = simulateMove(board, move[0], move[1], currentTurn);
                int eval = alphaBetaSearch(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    private int evaluateBoard(int[][] board, int myTurn) {
        int positionalScore = calculatePositionalScore(board, myTurn);
        int mobilityScore = calculateMobilityScore(board, myTurn);

        return positionalScore + (mobilityScore * MOBILITY_WEIGHT);
    }

    private int calculatePositionalScore(int[][] board, int myTurn) {
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] != 0) {
                    int pieceValue = BASE_EVAL_MATRIX[r][c];
                    
                    if (isUnstableDangerZone(r, c) && isCornerOwned(board, r, c)) {
                        pieceValue = Math.abs(pieceValue);
                    }

                    if (board[r][c] == myTurn) {
                        score += pieceValue;
                    } else {
                        score -= pieceValue;
                    }
                }
            }
        }
        return score;
    }

    private int calculateMobilityScore(int[][] board, int myTurn) {
        int myMobility = getValidMoves(board, myTurn).size();
        int oppMobility = getValidMoves(board, -myTurn).size();
        return myMobility - oppMobility;
    }

    private boolean isUnstableDangerZone(int r, int c) {
        return (r == 0 && c == 1) || (r == 1 && c == 0) || (r == 1 && c == 1) ||
               (r == 0 && c == 6) || (r == 1 && c == 7) || (r == 1 && c == 6) ||
               (r == 7 && c == 1) || (r == 6 && c == 0) || (r == 6 && c == 1) ||
               (r == 7 && c == 6) || (r == 6 && c == 7) || (r == 6 && c == 6);
    }

    private boolean isCornerOwned(int[][] board, int r, int c) {
        if (r <= 1 && c <= 1) return board[0][0] != 0;
        if (r <= 1 && c >= 6) return board[0][7] != 0;
        if (r >= 6 && c <= 1) return board[7][0] != 0;
        if (r >= 6 && c >= 6) return board[7][7] != 0;
        return false;
    }

    private boolean isGameOver(int[][] board) {
        return getValidMoves(board, 1).isEmpty() && getValidMoves(board, -1).isEmpty();
    }

    private int[][] simulateMove(int[][] board, int r, int c, int turn) {
        int[][] newBoard = new int[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, newBoard[i], 0, 8);
        }

        newBoard[r][c] = turn;
        int[] dirs = {-1, 0, 1};

        for (int dr : dirs) {
            for (int dc : dirs) {
                if (dr == 0 && dc == 0) continue;
                if (checkDirectionSim(newBoard, turn, r, c, dr, dc)) {
                    int nr = r + dr;
                    int nc = c + dc;
                    while (newBoard[nr][nc] == -turn) {
                        newBoard[nr][nc] = turn;
                        nr += dr;
                        nc += dc;
                    }
                }
            }
        }
        return newBoard;
    }

    private List<int[]> getValidMoves(int[][] board, int turn) {
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
                if (checkDirectionSim(board, turn, r, c, dr, dc)) {
                    return true;
                }
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

    private int[][] convertBoard(Integer[][] othello_array) {
        int[][] board = new int[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = (othello_array[r][c] == null) ? 0 : othello_array[r][c];
            }
        }
        return board;
    }
}