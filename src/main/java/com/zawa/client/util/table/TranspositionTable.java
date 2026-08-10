package com.zawa.client.util.table;

import java.util.Arrays;

public class TranspositionTable {
    private static final int TABLE_SIZE = 1 << 21;
    private static final int TABLE_MASK = TABLE_SIZE - 1;
    
    private final long[] tableLocks = new long[TABLE_SIZE];
    private final double[] tableScores = new double[TABLE_SIZE];
    private final long[] tableMoves = new long[TABLE_SIZE];

    public void clear() {
        Arrays.fill(tableLocks, 0L);
        Arrays.fill(tableScores, 0.0);
        Arrays.fill(tableMoves, -1L);
    }

    public void store(long hashValue, int depth, double score, int flag, int moveIndex) {
        int index = (int) (hashValue & TABLE_MASK);
        long lockValue = hashValue ^ (hashValue >>> 32);
        long moveData = ((long) depth << 32) | ((long) flag << 16);
        if (moveIndex != -1) {
            moveData |= ((long) moveIndex << 40);
        }
        tableLocks[index] = lockValue;
        tableScores[index] = score;
        tableMoves[index] = moveData;
    }

    public double getScore(long hashValue, int depth) {
        int index = (int) (hashValue & TABLE_MASK);
        long lockValue = hashValue ^ (hashValue >>> 32);
        if (tableLocks[index] == lockValue) {
            int tableDepth = (int) ((tableMoves[index] >>> 32) & 0xFF);
            if (tableDepth >= depth) {
                return tableScores[index];
            }
        }
        return -Double.MAX_VALUE;
    }

    public int getFlag(long hashValue) {
        int index = (int) (hashValue & TABLE_MASK);
        return (int) ((tableMoves[index] >>> 16) & 0x3);
    }

    public int getMove(long hashValue) {
        int index = (int) (hashValue & TABLE_MASK);
        long lockValue = hashValue ^ (hashValue >>> 32);
        if (tableLocks[index] == lockValue) {
            return (int) ((tableMoves[index] >>> 40) & 0x3F);
        }
        return -1;
    }
}