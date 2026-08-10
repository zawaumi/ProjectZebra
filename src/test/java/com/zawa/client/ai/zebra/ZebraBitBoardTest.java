package com.zawa.client.ai.zebra;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZebraBitBoardTest {
    @Test
    void initialPositionHasTheFourExpectedMoves() {
        long moves = ZebraBitBoard.legalMoves(ZebraBitBoard.INITIAL_BLACK, ZebraBitBoard.INITIAL_WHITE);
        long expected = (1L << 19) | (1L << 26) | (1L << 37) | (1L << 44);
        assertEquals(expected, moves);
        assertEquals(37, ZebraPerfectLineBook.find(ZebraBitBoard.INITIAL_BLACK, ZebraBitBoard.INITIAL_WHITE));
    }

    @Test
    void bitboardPlayMatchesArrayRulesAcrossRandomGames() {
        Random random = new Random(2026L);
        for (int game = 0; game < 80; game++) {
            long black = ZebraBitBoard.INITIAL_BLACK;
            long white = ZebraBitBoard.INITIAL_WHITE;
            int turn = 1;
            boolean passed = false;
            while (true) {
                long player = turn == 1 ? black : white;
                long opponent = turn == 1 ? white : black;
                long fastMoves = ZebraBitBoard.legalMoves(player, opponent);
                assertEquals(slowLegalMoves(player, opponent), fastMoves);
                if (fastMoves == 0L) {
                    if (passed) {
                        break;
                    }
                    passed = true;
                    turn = -turn;
                    continue;
                }
                passed = false;
                int selection = random.nextInt(Long.bitCount(fastMoves));
                long selected = fastMoves;
                while (selection-- > 0) {
                    selected &= selected - 1L;
                }
                int move = Long.numberOfTrailingZeros(selected);
                long fastFlips = ZebraBitBoard.flips(player, opponent, move);
                assertEquals(slowFlips(player, opponent, move), fastFlips);
                player |= fastFlips | (1L << move);
                opponent ^= fastFlips;
                if (turn == 1) {
                    black = player;
                    white = opponent;
                } else {
                    white = player;
                    black = opponent;
                }
                assertEquals(0L, black & white);
                turn = -turn;
            }
        }
    }

    @Test
    void allBoardTransformsPreserveLegalMoves() {
        long player = 0x0040382810080000L;
        long opponent = 0x0000041620100000L;
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        for (int transform = 0; transform < 8; transform++) {
            long transformedPlayer = ZebraBitBoard.transform(player, transform);
            long transformedOpponent = ZebraBitBoard.transform(opponent, transform);
            assertEquals(ZebraBitBoard.transform(legal, transform),
                    ZebraBitBoard.legalMoves(transformedPlayer, transformedOpponent));
            assertEquals(Long.bitCount(player), Long.bitCount(transformedPlayer));
            assertTrue((transformedPlayer & transformedOpponent) == 0L);
        }
    }

    private static long slowLegalMoves(long player, long opponent) {
        long legal = 0L;
        long empty = ~(player | opponent);
        while (empty != 0L) {
            int move = Long.numberOfTrailingZeros(empty);
            empty &= empty - 1L;
            if (slowFlips(player, opponent, move) != 0L) {
                legal |= 1L << move;
            }
        }
        return legal;
    }

    private static long slowFlips(long player, long opponent, int move) {
        int row = move / 8;
        int column = move % 8;
        long flipped = 0L;
        for (int rowStep = -1; rowStep <= 1; rowStep++) {
            for (int columnStep = -1; columnStep <= 1; columnStep++) {
                if (rowStep == 0 && columnStep == 0) {
                    continue;
                }
                int nextRow = row + rowStep;
                int nextColumn = column + columnStep;
                long line = 0L;
                while (nextRow >= 0 && nextRow < 8 && nextColumn >= 0 && nextColumn < 8) {
                    long square = 1L << (nextRow * 8 + nextColumn);
                    if ((opponent & square) != 0L) {
                        line |= square;
                    } else {
                        if (line != 0L && (player & square) != 0L) {
                            flipped |= line;
                        }
                        break;
                    }
                    nextRow += rowStep;
                    nextColumn += columnStep;
                }
            }
        }
        return flipped;
    }
}
