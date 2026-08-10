package com.zawa.client.ai.zebra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZebraSearchEngineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exactSearchMatchesIndependentSolver() {
        long[] position = latePosition(8, 717L);
        long player = position[0];
        long opponent = position[1];
        int empties = 64 - Long.bitCount(player | opponent);
        ZebraSearchEngine engine = new ZebraSearchEngine(ZebraPatternModel.loadDefault(), 16);
        ZebraSearchResult result = engine.findBestMoveAtDepth(player, opponent, empties);
        assertEquals(bruteRootScore(player, opponent), result.score());
        assertTrue(result.exact());
        assertTrue((ZebraBitBoard.legalMoves(player, opponent) & (1L << result.move())) != 0L);
    }

    @Test
    void exactRootWithOneEmptySquareReturnsTerminalScore() {
        long[] position = latePosition(1, 81_223L);
        long player = position[0];
        long opponent = position[1];
        ZebraSearchEngine engine = new ZebraSearchEngine(ZebraVanguardPatternModel.loadDefault(), 16,
                ZebraSearchProfile.vanguard());
        ZebraSearchResult result = engine.findBestMoveAtDepth(player, opponent, 1);
        assertEquals(bruteRootScore(player, opponent), result.score());
        assertTrue(result.exact());
    }

    @Test
    void hardDeadlineReturnsACompletedLegalFallback() {
        long[] position = latePosition(34, 911L);
        long legal = ZebraBitBoard.legalMoves(position[0], position[1]);
        ZebraSearchEngine engine = new ZebraSearchEngine(ZebraPatternModel.loadDefault(), 16);
        ZebraSearchResult result = engine.findBestMove(position[0], position[1], 80L, 100L);
        assertTrue((legal & (1L << result.move())) != 0L);
        assertTrue(result.elapsedMilliseconds() < 350L);
        assertTrue(result.completedDepth() >= 1);
    }

    @Test
    void vanguardKeepsTheProductionDeadlineMargin() {
        long[] position = latePosition(36, 2_026_072_2L);
        long legal = ZebraBitBoard.legalMoves(position[0], position[1]);
        ZebraSearchEngine engine = new ZebraSearchEngine(ZebraVanguardPatternModel.loadDefault(), 18,
                ZebraSearchProfile.vanguard());
        engine.findBestMove(position[0], position[1], 30L, 40L);
        ZebraSearchResult result = engine.findBestMove(position[0], position[1], 2_200L, 2_350L);
        assertTrue((legal & (1L << result.move())) != 0L);
        assertTrue(result.elapsedMilliseconds() < 2_700L);
        assertTrue(result.completedDepth() >= 1);
    }

    @Test
    void twentyTwoEmptyWldConvertsTheRecordedDrawIntoAWin() {
        long player = 0x0405061E3B230000L;
        long opponent = 0x0000F8E0C49C7C7CL;
        ZebraSearchEngine engine = new ZebraSearchEngine(ZebraVanguardPatternModel.loadDefault(), 22,
                ZebraSearchProfile.vanguard());
        ZebraSearchResult result = engine.findBestMove(player, opponent, 2_200L, 2_350L);
        assertEquals(7, result.move());
        assertTrue(result.score() > 30_000);
        assertTrue(result.outcomeSolved());
        assertTrue(result.elapsedMilliseconds() < 2_350L);
    }

    @Test
    void learnedModelRoundTripsWithoutChangingEvaluation() throws IOException {
        ZebraPatternModel model = ZebraPatternModel.loadDefault();
        long player = ZebraBitBoard.INITIAL_BLACK;
        long opponent = ZebraBitBoard.INITIAL_WHITE;
        model.learn(player, opponent, 0.75, 0.0025);
        int expected = model.evaluate(player, opponent);
        Path modelPath = temporaryDirectory.resolve("model.bin");
        model.save(modelPath);
        ZebraPatternModel loaded = ZebraPatternModel.load(modelPath);
        assertEquals(expected, loaded.evaluate(player, opponent));
    }

    private static long[] latePosition(int targetEmpties, long seed) {
        Random random = new Random(seed);
        long player = ZebraBitBoard.INITIAL_BLACK;
        long opponent = ZebraBitBoard.INITIAL_WHITE;
        boolean passed = false;
        while (64 - Long.bitCount(player | opponent) > targetEmpties) {
            long legal = ZebraBitBoard.legalMoves(player, opponent);
            if (legal == 0L) {
                if (passed) {
                    break;
                }
                passed = true;
                long swap = player;
                player = opponent;
                opponent = swap;
                continue;
            }
            passed = false;
            int selection = random.nextInt(Long.bitCount(legal));
            while (selection-- > 0) {
                legal &= legal - 1L;
            }
            int move = Long.numberOfTrailingZeros(legal);
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            long nextPlayer = opponent ^ flipped;
            long nextOpponent = player | flipped | (1L << move);
            player = nextPlayer;
            opponent = nextOpponent;
        }
        if (ZebraBitBoard.legalMoves(player, opponent) == 0L) {
            long swap = player;
            player = opponent;
            opponent = swap;
        }
        return new long[]{player, opponent};
    }

    private static int bruteRootScore(long player, long opponent) {
        int best = -31_500;
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        while (legal != 0L) {
            int move = Long.numberOfTrailingZeros(legal);
            legal &= legal - 1L;
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            int score = -brute(opponent ^ flipped, player | flipped | (1L << move), false);
            best = Math.max(best, score);
        }
        return best;
    }

    private static int brute(long player, long opponent, boolean passed) {
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        if (legal == 0L) {
            if (passed || ZebraBitBoard.legalMoves(opponent, player) == 0L) {
                int difference = Long.bitCount(player) - Long.bitCount(opponent);
                return difference > 0 ? 30_000 + difference : difference < 0 ? -30_000 + difference : 0;
            }
            return -brute(opponent, player, true);
        }
        int best = -31_500;
        while (legal != 0L) {
            int move = Long.numberOfTrailingZeros(legal);
            legal &= legal - 1L;
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            best = Math.max(best, -brute(opponent ^ flipped, player | flipped | (1L << move), false));
        }
        return best;
    }
}
