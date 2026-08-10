package com.zawa.client.ai.zebra;

public final class ZebraBitBoard {
    public static final long INITIAL_BLACK = 0x0000000810000000L;
    public static final long INITIAL_WHITE = 0x0000001008000000L;
    public static final long CORNERS = 0x8100000000000081L;
    public static final long EDGES = 0xFF818181818181FFL;

    private static final long INNER_FILES = 0x7E7E7E7E7E7E7E7EL;
    private static final long INNER_RANKS = 0x00FFFFFFFFFFFF00L;
    private static final long INNER_BOARD = 0x007E7E7E7E7E7E00L;

    private ZebraBitBoard() {
    }

    public static long[] fromArray(Integer[][] board, int turn) {
        long player = 0L;
        long opponent = 0L;
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                Integer value = board[row][column];
                if (value == null || value == 0) {
                    continue;
                }
                long square = 1L << (row * 8 + column);
                if (value == turn) {
                    player |= square;
                } else if (value == -turn) {
                    opponent |= square;
                }
            }
        }
        return new long[]{player, opponent};
    }

    public static long legalMoves(long player, long opponent) {
        long empty = ~(player | opponent);
        long legal = 0L;
        long masked = opponent & INNER_FILES;
        long captured = masked & (player >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        legal |= empty & (captured >>> 1);
        captured = masked & (player << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        legal |= empty & (captured << 1);

        masked = opponent & INNER_RANKS;
        captured = masked & (player >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        legal |= empty & (captured >>> 8);
        captured = masked & (player << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        legal |= empty & (captured << 8);

        masked = opponent & INNER_BOARD;
        captured = masked & (player >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        legal |= empty & (captured >>> 7);
        captured = masked & (player << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        legal |= empty & (captured << 7);
        captured = masked & (player >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        legal |= empty & (captured >>> 9);
        captured = masked & (player << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        legal |= empty & (captured << 9);
        return legal;
    }

    public static long flips(long player, long opponent, int move) {
        long moveBit = 1L << move;
        long flipped = 0L;
        long masked = opponent & INNER_FILES;
        long captured = masked & (moveBit >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        captured |= masked & (captured >>> 1);
        if ((player & (captured >>> 1)) != 0L) {
            flipped |= captured;
        }
        captured = masked & (moveBit << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        captured |= masked & (captured << 1);
        if ((player & (captured << 1)) != 0L) {
            flipped |= captured;
        }

        masked = opponent & INNER_RANKS;
        captured = masked & (moveBit >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        captured |= masked & (captured >>> 8);
        if ((player & (captured >>> 8)) != 0L) {
            flipped |= captured;
        }
        captured = masked & (moveBit << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        captured |= masked & (captured << 8);
        if ((player & (captured << 8)) != 0L) {
            flipped |= captured;
        }

        masked = opponent & INNER_BOARD;
        captured = masked & (moveBit >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        captured |= masked & (captured >>> 7);
        if ((player & (captured >>> 7)) != 0L) {
            flipped |= captured;
        }
        captured = masked & (moveBit << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        captured |= masked & (captured << 7);
        if ((player & (captured << 7)) != 0L) {
            flipped |= captured;
        }
        captured = masked & (moveBit >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        captured |= masked & (captured >>> 9);
        if ((player & (captured >>> 9)) != 0L) {
            flipped |= captured;
        }
        captured = masked & (moveBit << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        captured |= masked & (captured << 9);
        if ((player & (captured << 9)) != 0L) {
            flipped |= captured;
        }
        return flipped;
    }

    public static long adjacent(long board) {
        long eastWest = ((board << 1) & 0xFEFEFEFEFEFEFEFEL) | ((board >>> 1) & 0x7F7F7F7F7F7F7F7FL);
        long northSouth = (board << 8) | (board >>> 8);
        long diagonals = ((board << 7) & 0x7F7F7F7F7F7F7F7FL)
                | ((board >>> 7) & 0xFEFEFEFEFEFEFEFEL)
                | ((board << 9) & 0xFEFEFEFEFEFEFEFEL)
                | ((board >>> 9) & 0x7F7F7F7F7F7F7F7FL);
        return eastWest | northSouth | diagonals;
    }

    public static long orthogonalAdjacent(long board) {
        return ((board << 1) & 0xFEFEFEFEFEFEFEFEL)
                | ((board >>> 1) & 0x7F7F7F7F7F7F7F7FL)
                | (board << 8)
                | (board >>> 8);
    }

    public static int transformSquare(int square, int transform) {
        int row = square >>> 3;
        int column = square & 7;
        return switch (transform) {
            case 0 -> row * 8 + column;
            case 1 -> column * 8 + (7 - row);
            case 2 -> (7 - row) * 8 + (7 - column);
            case 3 -> (7 - column) * 8 + row;
            case 4 -> row * 8 + (7 - column);
            case 5 -> (7 - row) * 8 + column;
            case 6 -> column * 8 + row;
            case 7 -> (7 - column) * 8 + (7 - row);
            default -> throw new IllegalArgumentException("transform must be between 0 and 7");
        };
    }

    public static long transform(long board, int transform) {
        long result = 0L;
        long remaining = board;
        while (remaining != 0L) {
            int square = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1L;
            result |= 1L << transformSquare(square, transform);
        }
        return result;
    }

    public static long hash(long player, long opponent) {
        long first = mix64(player ^ 0x9E3779B97F4A7C15L);
        long second = mix64(opponent ^ 0xD1B54A32D192ED03L);
        long result = first ^ Long.rotateLeft(second, 29);
        return result == 0L ? 0xA0761D6478BD642FL : result;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
