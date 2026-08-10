package com.zawa.client.ai.ab;

import com.zawa.client.ai.AbstractClientAi;

import java.util.ArrayList;
import java.util.List;

public class ClientMobilityFrontierAlphaBetaAI extends AbstractClientAi {

    private static final int MIDGAME_DEPTH = 7;
    private static final int ENDGAME_DEPTH = 13;
    private static final int EXACT_WIN_BASE_SCORE = 1000000;

    private static final int[][] BASE_EVAL_MATRIX = {
        { 12000, -4000,  1000,   800,   800,  1000, -4000, 12000},
        { -4000, -6000,  -450,  -500,  -500,  -450, -6000, -4000},
        {  1000,  -450,    30,    10,    10,    30,  -450,  1000},
        {   800,  -500,    10,    50,    50,    10,  -500,   800},
        {   800,  -500,    10,    50,    50,    10,  -500,   800},
        {  1000,  -450,    30,    10,    10,    30,  -450,  1000},
        { -4000, -6000,  -450,  -500,  -500,  -450, -6000, -4000},
        { 12000, -4000,  1000,   800,   800,  1000, -4000, 12000}
    };

    public ClientMobilityFrontierAlphaBetaAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
    }

    public ClientMobilityFrontierAlphaBetaAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "MobilityFrontierAlphaBetaAI";
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

        int emptyCount = countEmptyCells(board);
        int depth = emptyCount <= ENDGAME_DEPTH ? emptyCount : MIDGAME_DEPTH;

        int[] bestMove = null;
        int maxEval = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        sortMoves(validMoves);

        for (int[] move : validMoves) {
            int[][] nextBoard = simulateMove(board, move[0], move[1], myturn);
            int eval = alphaBeta(nextBoard, depth - 1, alpha, beta, -myturn, myturn, emptyCount - 1);
            
            if (eval > maxEval) {
                maxEval = eval;
                bestMove = move;
            }
            alpha = Math.max(alpha, eval);
        }

        return bestMove != null ? new Integer[]{bestMove[0], bestMove[1]} : new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};
    }

    private int alphaBeta(int[][] board, int depth, int alpha, int beta, int currentTurn, int myTurn, int emptyCount) {
        if (depth == 0 || emptyCount == 0) {
            return evaluateDynamicHeuristics(board, myTurn, emptyCount, currentTurn);
        }

        List<int[]> moves = getValidMoves(board, currentTurn);

        if (moves.isEmpty()) {
            List<int[]> opponentMoves = getValidMoves(board, -currentTurn);
            if (opponentMoves.isEmpty()) {
                return evaluateExact(board, myTurn);
            }
            return alphaBeta(board, depth - 1, alpha, beta, -currentTurn, myTurn, emptyCount);
        }

        sortMoves(moves);

        if (currentTurn == myTurn) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : moves) {
                int[][] nextBoard = simulateMove(board, move[0], move[1], currentTurn);
                int eval = alphaBeta(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn, emptyCount - 1);
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
                int eval = alphaBeta(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn, emptyCount - 1);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    private void sortMoves(List<int[]> moves) {
        moves.sort((m1, m2) -> {
            int score1 = BASE_EVAL_MATRIX[m1[0]][m1[1]];
            int score2 = BASE_EVAL_MATRIX[m2[0]][m2[1]];
            return Integer.compare(score2, score1);
        });
    }

    private int evaluateDynamicHeuristics(int[][] board, int myTurn, int emptyCount, int currentTurn) {
        if (emptyCount == 0) {
            return evaluateExact(board, myTurn);
        }

        int positionalWeight = getPositionalWeight(emptyCount);
        int mobilityWeight = getMobilityWeight(emptyCount);
        int frontierWeight = getFrontierWeight(emptyCount);
        int parityWeight = getParityWeight(emptyCount);

        int positional = 0;
        int myMobility = getValidMoves(board, myTurn).size();
        int oppMobility = getValidMoves(board, -myTurn).size();
        int myFrontier = 0;
        int oppFrontier = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                if (piece != 0) {
                    int val = BASE_EVAL_MATRIX[r][c];
                    
                    if (isUnstableDangerZone(r, c) && isCornerOwned(board, r, c)) {
                        val = Math.abs(val);
                    }

                    boolean isFrontier = isExposedToEmpty(board, r, c);

                    if (piece == myTurn) {
                        positional += val;
                        if (isFrontier) myFrontier++;
                    } else {
                        positional -= val;
                        if (isFrontier) oppFrontier++;
                    }
                }
            }
        }

        int mobilityScore = mobilityWeight * (myMobility - oppMobility);
        int frontierScore = frontierWeight * (oppFrontier - myFrontier);
        
        int parityScore = 0;
        if (emptyCount <= 20) {
            if (currentTurn == myTurn) {
                parityScore = (emptyCount % 2 != 0) ? parityWeight : -parityWeight;
            } else {
                parityScore = (emptyCount % 2 == 0) ? parityWeight : -parityWeight;
            }
        }

        return (positional * positionalWeight / 100) + mobilityScore + frontierScore + parityScore;
    }

    private int getPositionalWeight(int emptyCount) {
        if (emptyCount > 45) return 100;
        if (emptyCount > 20) return 60;
        return 20;
    }

    private int getMobilityWeight(int emptyCount) {
        if (emptyCount > 45) return 10;
        if (emptyCount > 20) return 40;
        return 20;
    }

    private int getFrontierWeight(int emptyCount) {
        if (emptyCount > 45) return 20;
        if (emptyCount > 20) return 30;
        return 10;
    }

    private int getParityWeight(int emptyCount) {
        if (emptyCount > 20) return 0;
        return 150;
    }

    private int evaluateExact(int[][] board, int myTurn) {
        int myCount = 0;
        int oppCount = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == myTurn) {
                    myCount++;
                } else if (board[r][c] == -myTurn) {
                    oppCount++;
                }
            }
        }

        int difference = myCount - oppCount;
        if (myCount > oppCount) {
            return EXACT_WIN_BASE_SCORE + difference;
        } else if (myCount < oppCount) {
            return -EXACT_WIN_BASE_SCORE + difference;
        }
        return 0;
    }

    private boolean isExposedToEmpty(int[][] board, int r, int c) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8 && board[nr][nc] == 0) {
                    return true;
                }
            }
        }
        return false;
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

    private int countEmptyCells(int[][] board) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == 0) {
                    count++;
                }
            }
        }
        return count;
    }
}