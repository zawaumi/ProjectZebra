package com.zawa.client.ai.learn;

import com.zawa.client.ai.AbstractClientAi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientMachineLearningAI extends AbstractClientAi {

    private static final String WEIGHT_FILE_PATH = "ml_othello_weights.csv";
    private static final int SEARCH_DEPTH = 2;
    private static final double LEARNING_RATE = 0.01;
    private static final double DISCOUNT_FACTOR = 0.95;

    private static final double MAX_ERROR = 1000.0;
    private static final double MAX_WEIGHT = 50000.0;
    private static final double WEIGHT_DECAY = 0.99999;

    private static double[][] weights = new double[8][8];
    private static boolean weightsLoaded = false;

    private double lastEvaluation = 0.0;
    private int[][] lastBoard = null;

    private static final double[][] INITIAL_WEIGHTS = {
            { 12000.0, -4000.0,  1000.0,   800.0,   800.0,  1000.0, -4000.0, 12000.0},
            { -4000.0, -6000.0,  -450.0,  -500.0,  -500.0,  -450.0, -6000.0, -4000.0},
            {  1000.0,  -450.0,    30.0,    10.0,    10.0,    30.0,  -450.0,  1000.0},
            {   800.0,  -500.0,    10.0,    50.0,    50.0,    10.0,  -500.0,   800.0},
            {   800.0,  -500.0,    10.0,    50.0,    50.0,    10.0,  -500.0,   800.0},
            {  1000.0,  -450.0,    30.0,    10.0,    10.0,    30.0,  -450.0,  1000.0},
            { -4000.0, -6000.0,  -450.0,  -500.0,  -500.0,  -450.0, -6000.0, -4000.0},
            { 12000.0, -4000.0,  1000.0,   800.0,   800.0,  1000.0, -4000.0, 12000.0}
    };

    public ClientMachineLearningAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
    }

    public ClientMachineLearningAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "MachineLearningAI";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        if (!weightsLoaded) {
            loadWeightsFromFile();
        }

        int[][] board = convertBoard(othello_array);

        if (lastBoard != null) {
            updateWeights(board, myturn);
        }

        List<int[]> validMoves = getValidMoves(board, myturn);

        if (validMoves.isEmpty()) {
            return null;
        }

        if (validMoves.size() == 1) {
            recordState(board, myturn, validMoves.get(0));
            return new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};
        }

        int[] bestMove = null;
        double maxEval = Double.NEGATIVE_INFINITY;
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        for (int[] move : validMoves) {
            int[][] nextBoard = simulateMove(board, move[0], move[1], myturn);
            double eval = alphaBeta(nextBoard, SEARCH_DEPTH - 1, alpha, beta, -myturn, myturn);

            if (eval > maxEval) {
                maxEval = eval;
                bestMove = move;
            }
            alpha = Math.max(alpha, eval);
        }

        if (bestMove != null) {
            recordState(board, myturn, bestMove);
            return new Integer[]{bestMove[0], bestMove[1]};
        }

        recordState(board, myturn, validMoves.get(0));
        return new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};
    }

    private void recordState(int[][] board, int myturn, int[] move) {
        this.lastBoard = simulateMove(board, move[0], move[1], myturn);
        this.lastEvaluation = evaluateBoard(this.lastBoard, myturn);
    }

    private void updateWeights(int[][] currentBoard, int myturn) {
        double currentEvaluation = evaluateBoard(currentBoard, myturn);
        double error = currentEvaluation - lastEvaluation;

        if (error > MAX_ERROR) error = MAX_ERROR;
        if (error < -MAX_ERROR) error = -MAX_ERROR;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (lastBoard[r][c] == myturn) {
                    weights[r][c] += LEARNING_RATE * error * DISCOUNT_FACTOR;
                } else if (lastBoard[r][c] == -myturn) {
                    weights[r][c] -= LEARNING_RATE * error * DISCOUNT_FACTOR;
                }

                weights[r][c] *= WEIGHT_DECAY;

                if (weights[r][c] > MAX_WEIGHT) weights[r][c] = MAX_WEIGHT;
                if (weights[r][c] < -MAX_WEIGHT) weights[r][c] = -MAX_WEIGHT;

                if (Double.isNaN(weights[r][c]) || Double.isInfinite(weights[r][c])) {
                    weights[r][c] = INITIAL_WEIGHTS[r][c];
                }
            }
        }
    }

    public static void loadWeightsFromFile() {
        File file = new File(WEIGHT_FILE_PATH);
        boolean corrupted = false;

        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                for (int r = 0; r < 8; r++) {
                    String line = br.readLine();
                    if (line != null) {
                        String[] values = line.split(",");
                        for (int c = 0; c < 8; c++) {
                            double val = Double.parseDouble(values[c]);
                            if (Double.isNaN(val) || Double.isInfinite(val)) {
                                corrupted = true;
                            } else {
                                weights[r][c] = val;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                corrupted = true;
            }
        } else {
            corrupted = true;
        }

        if (corrupted) {
            System.out.println("Weights file is missing or corrupted (NaN). Initializing with default values...");
            initializeDefaultWeights();
        }
        weightsLoaded = true;
    }

    public static void saveWeightsToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(WEIGHT_FILE_PATH))) {
            for (int r = 0; r < 8; r++) {
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < 8; c++) {
                    if (Double.isNaN(weights[r][c]) || Double.isInfinite(weights[r][c])) {
                        weights[r][c] = INITIAL_WEIGHTS[r][c];
                    }
                    sb.append(weights[r][c]);
                    if (c < 7) {
                        sb.append(",");
                    }
                }
                bw.write(sb.toString());
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void initializeDefaultWeights() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                weights[r][c] = INITIAL_WEIGHTS[r][c];
            }
        }
    }

    private double alphaBeta(int[][] board, int depth, double alpha, double beta, int currentTurn, int myTurn) {
        if (depth == 0 || isGameOver(board)) {
            return evaluateBoard(board, myTurn);
        }

        List<int[]> moves = getValidMoves(board, currentTurn);

        if (moves.isEmpty()) {
            return alphaBeta(board, depth - 1, alpha, beta, -currentTurn, myTurn);
        }

        if (currentTurn == myTurn) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int[] move : moves) {
                int[][] nextBoard = simulateMove(board, move[0], move[1], currentTurn);
                double eval = alphaBeta(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int[] move : moves) {
                int[][] nextBoard = simulateMove(board, move[0], move[1], currentTurn);
                double eval = alphaBeta(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    private double evaluateBoard(int[][] board, int myTurn) {
        double score = 0.0;
        int myMobility = 0;
        int oppMobility = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == myTurn) {
                    score += weights[r][c];
                } else if (board[r][c] == -myTurn) {
                    score -= weights[r][c];
                } else if (board[r][c] == 0) {
                    if (isValidMoveSim(board, myTurn, r, c)) myMobility++;
                    if (isValidMoveSim(board, -myTurn, r, c)) oppMobility++;
                }
            }
        }

        return score + (myMobility - oppMobility) * 20.0;
    }

    private boolean isGameOver(int[][] board) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == 0) {
                    if (isValidMoveSim(board, 1, r, c) || isValidMoveSim(board, -1, r, c)) {
                        return false;
                    }
                }
            }
        }
        return true;
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
        List<int[]> moves = new ArrayList<>(16);
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