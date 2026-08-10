package com.zawa.client.ai.zebra;

public final class ZebraStrategicFeatures {
    public static final int COUNT = 9;

    private static final long NON_CORNER_EDGES = ZebraBitBoard.EDGES & ~ZebraBitBoard.CORNERS;
    private static final int[][] CORNER_RAYS = {
            {0, 1, 8}, {7, -1, 8}, {56, 1, -8}, {63, -1, -8}
    };
    private static final long[] CORNER_NEIGHBORS = {
            (1L << 1) | (1L << 8) | (1L << 9),
            (1L << 6) | (1L << 14) | (1L << 15),
            (1L << 48) | (1L << 49) | (1L << 57),
            (1L << 54) | (1L << 55) | (1L << 62)
    };

    private ZebraStrategicFeatures() {
    }

    public static void extract(long player, long opponent, long playerMoves, int baseScore, double[] result) {
        if (result.length < COUNT) {
            throw new IllegalArgumentException("feature buffer is too small");
        }
        long empty = ~(player | opponent);
        long opponentMoves = ZebraBitBoard.legalMoves(opponent, player);
        long emptyAdjacent = ZebraBitBoard.adjacent(empty);
        long dangerous = emptyCornerNeighbors(empty);
        result[0] = (Long.bitCount(playerMoves) - Long.bitCount(opponentMoves)) / 16.0;
        result[1] = (Long.bitCount(empty & ZebraBitBoard.adjacent(opponent))
                - Long.bitCount(empty & ZebraBitBoard.adjacent(player))) / 32.0;
        result[2] = (Long.bitCount(opponent & emptyAdjacent) - Long.bitCount(player & emptyAdjacent)) / 32.0;
        result[3] = (Long.bitCount(player) - Long.bitCount(opponent)) / 64.0;
        result[4] = (Long.bitCount(player & ZebraBitBoard.CORNERS)
                - Long.bitCount(opponent & ZebraBitBoard.CORNERS)) / 4.0;
        result[5] = (Long.bitCount(player & NON_CORNER_EDGES)
                - Long.bitCount(opponent & NON_CORNER_EDGES)) / 24.0;
        result[6] = (Long.bitCount(stableEdgeDiscs(player))
                - Long.bitCount(stableEdgeDiscs(opponent))) / 28.0;
        result[7] = (Long.bitCount(opponent & dangerous) - Long.bitCount(player & dangerous)) / 12.0;
        result[8] = Math.max(-1.5, Math.min(1.5, baseScore / 8_000.0));
    }

    private static long emptyCornerNeighbors(long empty) {
        long result = 0L;
        long corners = empty & ZebraBitBoard.CORNERS;
        while (corners != 0L) {
            int corner = Long.numberOfTrailingZeros(corners);
            corners &= corners - 1L;
            result |= CORNER_NEIGHBORS[switch (corner) {
                case 0 -> 0;
                case 7 -> 1;
                case 56 -> 2;
                case 63 -> 3;
                default -> throw new IllegalStateException("invalid corner");
            }];
        }
        return result;
    }

    private static long stableEdgeDiscs(long board) {
        long stable = 0L;
        for (int[] rays : CORNER_RAYS) {
            int corner = rays[0];
            long cornerBit = 1L << corner;
            if ((board & cornerBit) == 0L) {
                continue;
            }
            stable |= cornerBit;
            stable |= stableRay(board, corner, rays[1]);
            stable |= stableRay(board, corner, rays[2]);
        }
        return stable;
    }

    private static long stableRay(long board, int corner, int step) {
        long result = 0L;
        int square = corner + step;
        for (int index = 1; index < 8; index++, square += step) {
            long bit = 1L << square;
            if ((board & bit) == 0L) {
                break;
            }
            result |= bit;
        }
        return result;
    }
}
