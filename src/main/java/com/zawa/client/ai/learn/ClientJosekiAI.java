package com.zawa.client.ai.learn;

import com.zawa.client.ai.AbstractClientAi;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class ClientJosekiAI extends AbstractClientAi {

    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.dir") + File.separator + "joseki.db";
    private static final Map<String, Integer[]> josekiDB = new ConcurrentHashMap<>();
    private static final Map<String, Integer[]> newJosekiToSave = new ConcurrentHashMap<>();
    private static boolean isLoaded = false;

    private static final int MIDGAME_DEPTH = 9;
    private static final int ENDGAME_DEPTH = 17;
    private static final int EXACT_WIN_BASE_SCORE = 1000000;

    private static final long[][][] ZOBRIST = new long[2][8][8];
    private static final long ZOBRIST_TURN;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Random rnd = new Random(2026);
        for (int p = 0; p < 2; p++) {
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    ZOBRIST[p][r][c] = rnd.nextLong();
                }
            }
        }
        ZOBRIST_TURN = rnd.nextLong();
    }

    private static final int[][] BASE_MATRIX = {
            { 12000, -4000,  1000,   800,   800,  1000, -4000, 12000},
            { -4000, -6000,  -450,  -500,  -500,  -450, -6000, -4000},
            {  1000,  -450,    30,    10,    10,    30,  -450,  1000},
            {   800,  -500,    10,    50,    50,    10,  -500,   800},
            {   800,  -500,    10,    50,    50,    10,  -500,   800},
            {  1000,  -450,    30,    10,    10,    30,  -450,  1000},
            { -4000, -6000,  -450,  -500,  -500,  -450, -6000, -4000},
            { 12000, -4000,  1000,   800,   800,  1000, -4000, 12000}
    };

    private static class TTEntry {
        int depth;
        int value;
        int flag;
        int[] bestMove;

        TTEntry(int depth, int value, int flag, int[] bestMove) {
            this.depth = depth;
            this.value = value;
            this.flag = flag;
            this.bestMove = bestMove;
        }
    }

    private Map<Long, TTEntry> transTable;

    public ClientJosekiAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
        this.ai_name = "JosekiAI_2026_PVS";
        ensureLoaded();
    }

    public ClientJosekiAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "JosekiAI_2026_PVS";
        ensureLoaded();
    }

    private static synchronized void ensureLoaded() {
        if (!isLoaded) {
            loadDB();
            isLoaded = true;
        }
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        int[][] board = convertBoard(othello_array);
        String stateKey = boardToKey(board, myturn);

        if (josekiDB.containsKey(stateKey)) {
            Integer[] move = josekiDB.get(stateKey);
            if (isValidMoveSim(board, myturn, move[0], move[1])) {
                return move;
            }
        }

        List<int[]> validMoves = getValidMoves(board, myturn);
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};

        int emptyCount = countEmptyCells(board);
        int depth = emptyCount <= ENDGAME_DEPTH ? emptyCount : MIDGAME_DEPTH;

        transTable = new HashMap<>();
        int[] bestMove = null;
        int maxEval = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE + 1;
        int beta = Integer.MAX_VALUE - 1;

        sortMoves(validMoves, null);

        boolean first = true;
        for (int[] move : validMoves) {
            int[][] nextBoard = simulateMove(board, move[0], move[1], myturn);
            int eval;
            if (first) {
                eval = pvs(nextBoard, depth - 1, alpha, beta, -myturn, myturn, emptyCount - 1);
                first = false;
            } else {
                eval = pvs(nextBoard, depth - 1, alpha, alpha + 1, -myturn, myturn, emptyCount - 1);
                if (eval > alpha && eval < beta) {
                    eval = pvs(nextBoard, depth - 1, eval, beta, -myturn, myturn, emptyCount - 1);
                }
            }

            if (eval > maxEval) {
                maxEval = eval;
                bestMove = move;
            }
            alpha = Math.max(alpha, eval);
        }

        return bestMove != null ? new Integer[]{bestMove[0], bestMove[1]} : new Integer[]{validMoves.get(0)[0], validMoves.get(0)[1]};
    }

    private long computeHash(int[][] board, int currentTurn) {
        long hash = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                if (piece != 0) {
                    int pIdx = piece == 1 ? 0 : 1;
                    hash ^= ZOBRIST[pIdx][r][c];
                }
            }
        }
        if (currentTurn == -1) {
            hash ^= ZOBRIST_TURN;
        }
        return hash;
    }

    private int pvs(int[][] board, int depth, int alpha, int beta, int currentTurn, int myTurn, int emptyCount) {
        if (depth == 0 || emptyCount == 0) {
            return evaluateDynamicHeuristics(board, myTurn, emptyCount, currentTurn);
        }

        long hash = computeHash(board, currentTurn);
        TTEntry tt = transTable.get(hash);
        if (tt != null && tt.depth >= depth) {
            if (tt.flag == 0) return tt.value;
            if (tt.flag == 1 && tt.value <= alpha) return alpha;
            if (tt.flag == 2 && tt.value >= beta) return beta;
        }

        List<int[]> moves = getValidMoves(board, currentTurn);

        if (moves.isEmpty()) {
            List<int[]> opponentMoves = getValidMoves(board, -currentTurn);
            if (opponentMoves.isEmpty()) {
                return evaluateExact(board, myTurn);
            }
            return pvs(board, depth - 1, alpha, beta, -currentTurn, myTurn, emptyCount);
        }

        sortMoves(moves, tt != null ? tt.bestMove : null);

        int alphaOrig = alpha;
        int betaOrig = beta;
        int bestVal;
        int[] bestMoveNode = moves.get(0);
        boolean first = true;

        if (currentTurn == myTurn) {
            bestVal = Integer.MIN_VALUE + 1;
            for (int[] move : moves) {
                int[][] nextBoard = simulateMove(board, move[0], move[1], currentTurn);
                int eval;
                if (first) {
                    eval = pvs(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn, emptyCount - 1);
                    first = false;
                } else {
                    eval = pvs(nextBoard, depth - 1, alpha, alpha + 1, -currentTurn, myTurn, emptyCount - 1);
                    if (eval > alpha && eval < beta) {
                        eval = pvs(nextBoard, depth - 1, eval, beta, -currentTurn, myTurn, emptyCount - 1);
                    }
                }
                if (eval > bestVal) {
                    bestVal = eval;
                    bestMoveNode = move;
                }
                alpha = Math.max(alpha, bestVal);
                if (alpha >= beta) break;
            }
        } else {
            bestVal = Integer.MAX_VALUE - 1;
            for (int[] move : moves) {
                int[][] nextBoard = simulateMove(board, move[0], move[1], currentTurn);
                int eval;
                if (first) {
                    eval = pvs(nextBoard, depth - 1, alpha, beta, -currentTurn, myTurn, emptyCount - 1);
                    first = false;
                } else {
                    eval = pvs(nextBoard, depth - 1, beta - 1, beta, -currentTurn, myTurn, emptyCount - 1);
                    if (eval > alpha && eval < beta) {
                        eval = pvs(nextBoard, depth - 1, alpha, eval, -currentTurn, myTurn, emptyCount - 1);
                    }
                }
                if (eval < bestVal) {
                    bestVal = eval;
                    bestMoveNode = move;
                }
                beta = Math.min(beta, bestVal);
                if (alpha >= beta) break;
            }
        }

        int flag = 0;
        if (bestVal <= alphaOrig) flag = 1;
        else if (bestVal >= betaOrig) flag = 2;

        transTable.put(hash, new TTEntry(depth, bestVal, flag, bestMoveNode));
        return bestVal;
    }

    private void sortMoves(List<int[]> moves, int[] ttBestMove) {
        moves.sort((m1, m2) -> {
            if (ttBestMove != null) {
                boolean m1Best = (m1[0] == ttBestMove[0] && m1[1] == ttBestMove[1]);
                boolean m2Best = (m2[0] == ttBestMove[0] && m2[1] == ttBestMove[1]);
                if (m1Best && !m2Best) return -1;
                if (!m1Best && m2Best) return 1;
            }
            return Integer.compare(BASE_MATRIX[m2[0]][m2[1]], BASE_MATRIX[m1[0]][m1[1]]);
        });
    }

    private int evaluateDynamicHeuristics(int[][] board, int myTurn, int emptyCount, int currentTurn) {
        if (emptyCount == 0) {
            return evaluateExact(board, myTurn);
        }

        double phase = (64.0 - emptyCount) / 64.0;
        int positionalWeight = (int)(100 - phase * 60);
        int mobilityWeight = (int)(20 + Math.sin(phase * Math.PI) * 60);
        int frontierWeight = (int)(10 + phase * 40);
        int parityWeight = emptyCount <= 16 ? 200 : 0;

        int positional = 0;
        int myMobility = getValidMoves(board, myTurn).size();
        int oppMobility = getValidMoves(board, -myTurn).size();
        int myFrontier = 0;
        int oppFrontier = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                if (piece != 0) {
                    int val = BASE_MATRIX[r][c];
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

        if (emptyCount <= 16) {
            if (currentTurn == myTurn) {
                parityScore = (emptyCount % 2 != 0) ? parityWeight : -parityWeight;
            } else {
                parityScore = (emptyCount % 2 == 0) ? parityWeight : -parityWeight;
            }
        }

        return (positional * positionalWeight / 100) + mobilityScore + frontierScore + parityScore;
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

    public static void registerWinningMove(String key, int r, int c) {
        int[][] board = keyToBoard(key, 1);
        for (int i = 0; i < 8; i++) {
            int[][] tBoard = transformBoard(board, i);
            int[] tMove = transformCoord(r, c, i);
            String tKey = boardToKey(tBoard, 1);
            if (!josekiDB.containsKey(tKey)) {
                Integer[] move = new Integer[]{tMove[0], tMove[1]};
                josekiDB.put(tKey, move);
                newJosekiToSave.put(tKey, move);
            }
        }
    }

    private static int[][] transformBoard(int[][] board, int type) {
        int[][] res = new int[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int[] n = transformCoord(r, c, type);
                res[n[0]][n[1]] = board[r][c];
            }
        }
        return res;
    }

    private static int[] transformCoord(int r, int c, int type) {
        switch (type) {
            case 0: return new int[]{r, c};
            case 1: return new int[]{c, 7 - r};
            case 2: return new int[]{7 - r, 7 - c};
            case 3: return new int[]{7 - c, r};
            case 4: return new int[]{r, 7 - c};
            case 5: return new int[]{7 - r, c};
            case 6: return new int[]{c, r};
            case 7: return new int[]{7 - c, 7 - r};
        }
        return new int[]{r, c};
    }

    private static int[][] keyToBoard(String key, int myTurn) {
        int[][] board = new int[8][8];
        for (int i = 0; i < 64; i++) {
            char ch = key.charAt(i);
            int r = i / 8;
            int c = i % 8;
            if (ch == '1') board[r][c] = myTurn;
            else if (ch == '2') board[r][c] = -myTurn;
        }
        return board;
    }

    public static String boardToKey(int[][] board, int myTurn) {
        StringBuilder sb = new StringBuilder(64);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == myTurn) sb.append('1');
                else if (board[r][c] == -myTurn) sb.append('2');
                else sb.append('0');
            }
        }
        return sb.toString();
    }

    private static synchronized void loadDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS joseki (state_key VARCHAR(64) PRIMARY KEY, r INT, c INT)");
            try (ResultSet rs = stmt.executeQuery("SELECT state_key, r, c FROM joseki")) {
                while (rs.next()) {
                    josekiDB.put(rs.getString("state_key"), new Integer[]{rs.getInt("r"), rs.getInt("c")});
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static synchronized void saveDB() {
        if (newJosekiToSave.isEmpty()) return;
        String sql = "INSERT OR IGNORE INTO joseki (state_key, r, c) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Map.Entry<String, Integer[]> entry : newJosekiToSave.entrySet()) {
                pstmt.setString(1, entry.getKey());
                pstmt.setInt(2, entry.getValue()[0]);
                pstmt.setInt(3, entry.getValue()[1]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            newJosekiToSave.clear();
        } catch (Exception e) { e.printStackTrace(); }
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
            if (board[nr][nc] == -turn) hasOpponent = true;
            else if (board[nr][nc] == turn) return hasOpponent;
            else break;
            nr += dr;
            nc += dc;
        }
        return false;
    }

    private int[][] simulateMove(int[][] board, int r, int c, int turn) {
        int[][] newBoard = new int[8][8];
        for (int i = 0; i < 8; i++) System.arraycopy(board[i], 0, newBoard[i], 0, 8);
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
}