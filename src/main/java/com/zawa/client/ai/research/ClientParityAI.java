package com.zawa.client.ai.research;

import com.zawa.client.ai.AbstractClientAi;

import java.util.ArrayList;
import java.util.List;

public class ClientParityAI extends AbstractClientAi {

    private static final int PARITY_WEIGHT = 5000;
    private static final int MOBILITY_WEIGHT = 15;

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

    public ClientParityAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
    }

    public ClientParityAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "ParityAI";
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

        int[][] regions = new int[8][8];
        int[] regionSizes = calculateEmptyRegions(board, regions);

        int[] bestMove = null;
        int maxScore = Integer.MIN_VALUE;

        for (int[] move : validMoves) {
            int r = move[0];
            int c = move[1];
            
            int regionId = regions[r][c];
            int regionSize = regionSizes[regionId];

            int parityScore = (regionSize % 2 != 0) ? PARITY_WEIGHT : -PARITY_WEIGHT;
            int positionalScore = calculatePositionalScore(board, r, c, myturn);
            
            int[][] nextBoard = simulateMove(board, r, c, myturn);
            int mobilityScore = calculateMobilityScore(nextBoard, myturn);

            int totalScore = parityScore + positionalScore + (mobilityScore * MOBILITY_WEIGHT);

            if (totalScore > maxScore) {
                maxScore = totalScore;
                bestMove = move;
            }
        }

        return bestMove != null ? new Integer[]{bestMove[0], bestMove[1]} : new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};
    }

    private int[] calculateEmptyRegions(int[][] board, int[][] regions) {
        int currentRegionId = 1;
        int[] regionSizes = new int[65];
        boolean[][] visited = new boolean[8][8];

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == 0 && !visited[r][c]) {
                    int size = exploreRegion(board, visited, regions, r, c, currentRegionId);
                    regionSizes[currentRegionId] = size;
                    currentRegionId++;
                }
            }
        }
        return regionSizes;
    }

    private int exploreRegion(int[][] board, boolean[][] visited, int[][] regions, int r, int c, int regionId) {
        visited[r][c] = true;
        regions[r][c] = regionId;
        int size = 1;

        int[] dirs = {-1, 0, 1};
        for (int dr : dirs) {
            for (int dc : dirs) {
                if (dr == 0 && dc == 0) continue;
                
                int nr = r + dr;
                int nc = c + dc;
                
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                    if (board[nr][nc] == 0 && !visited[nr][nc]) {
                        size += exploreRegion(board, visited, regions, nr, nc, regionId);
                    }
                }
            }
        }
        return size;
    }

    private int calculatePositionalScore(int[][] board, int r, int c, int myTurn) {
        int score = BASE_EVAL_MATRIX[r][c];

        if (isUnstableDangerZone(r, c) && isCornerOwned(board, r, c)) {
            score = Math.abs(score);
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