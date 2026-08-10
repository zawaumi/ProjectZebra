package com.zawa.client.ai.zebra;

import java.util.HashMap;
import java.util.Map;

public final class ZebraPerfectLineBook {
    private static final String OPTIMAL_GAME = "F5D6C3D3C4F4F6F3E6E7D7C5B6D8C6C7D2B5A5A6A7G5E3B4C8G6G4C2E8D1F7E2G3H4F1E1F2G1B1F8G8B3H3B2H5B7A3A4A1A2C1H2H1G2B8A8G7H8H7H6";
    private static final Map<PositionKey, Integer> MOVES = buildMoves();

    private ZebraPerfectLineBook() {
    }

    public static int find(long player, long opponent) {
        return MOVES.getOrDefault(new PositionKey(player, opponent), -1);
    }

    private static Map<PositionKey, Integer> buildMoves() {
        Map<PositionKey, Integer> moves = new HashMap<>();
        long black = ZebraBitBoard.INITIAL_BLACK;
        long white = ZebraBitBoard.INITIAL_WHITE;
        int turn = 1;
        for (int offset = 0; offset < OPTIMAL_GAME.length(); offset += 2) {
            int column = OPTIMAL_GAME.charAt(offset) - 'A';
            int row = OPTIMAL_GAME.charAt(offset + 1) - '1';
            int move = row * 8 + column;
            long player = turn == 1 ? black : white;
            long opponent = turn == 1 ? white : black;
            if ((ZebraBitBoard.legalMoves(player, opponent) & (1L << move)) == 0L) {
                throw new IllegalStateException("Invalid optimal Othello game record");
            }
            for (int transform = 0; transform < 8; transform++) {
                long transformedPlayer = ZebraBitBoard.transform(player, transform);
                long transformedOpponent = ZebraBitBoard.transform(opponent, transform);
                int transformedMove = ZebraBitBoard.transformSquare(move, transform);
                moves.putIfAbsent(new PositionKey(transformedPlayer, transformedOpponent), transformedMove);
            }
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            player |= flipped | (1L << move);
            opponent ^= flipped;
            if (turn == 1) {
                black = player;
                white = opponent;
            } else {
                white = player;
                black = opponent;
            }
            turn = -turn;
        }
        return Map.copyOf(moves);
    }

    private record PositionKey(long player, long opponent) {
    }
}
