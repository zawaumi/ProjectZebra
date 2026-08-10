package com.zawa.client.ai.zebra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZebraOpeningPolicyTest {
    @Test
    void bundledPolicyContainsLegalExpertMovesAcrossSymmetries() {
        ZebraOpeningPolicy policy = ZebraOpeningPolicy.defaultPolicy();
        assertTrue(policy.size() > 6_000);
        long player = ZebraBitBoard.INITIAL_BLACK;
        long opponent = ZebraBitBoard.INITIAL_WHITE;
        int firstMove = 37;
        long flipped = ZebraBitBoard.flips(player, opponent, firstMove);
        long nextPlayer = opponent ^ flipped;
        long nextOpponent = player | flipped | (1L << firstMove);
        int expected = policy.find(nextPlayer, nextOpponent);
        assertTrue((ZebraBitBoard.legalMoves(nextPlayer, nextOpponent) & (1L << expected)) != 0L);
        for (int transform = 0; transform < 8; transform++) {
            long transformedPlayer = ZebraBitBoard.transform(nextPlayer, transform);
            long transformedOpponent = ZebraBitBoard.transform(nextOpponent, transform);
            assertEquals(ZebraBitBoard.transformSquare(expected, transform),
                    policy.find(transformedPlayer, transformedOpponent));
        }
    }
}
