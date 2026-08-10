package com.zawa.client.ai.zebra;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ZebraSearchTuningBenchmark {
    private ZebraSearchTuningBenchmark() {
    }

    public static void main(String[] arguments) {
        ZebraVanguardPatternModel model = ZebraVanguardPatternModel.loadDefault();
        List<Position> warmup = positions(4, 36, 91_711L);
        compare(model, warmup, 7, ZebraSearchProfile.vanguardControl(),
                ZebraSearchProfile.vanguardAssociative(), false);

        List<Position> midgame = positions(24, 34, 50_027L);
        Comparison associative = compare(model, midgame, 9, ZebraSearchProfile.vanguardControl(),
                ZebraSearchProfile.vanguardAssociative(), false);
        System.out.printf("ASSOCIATIVE positions=%d scoreMismatches=%d moveMismatches=%d nodes=%d/%d ratio=%.4f time=%d/%dms ratio=%.4f%n",
                midgame.size(), associative.scoreMismatches(), associative.moveMismatches(),
                associative.controlNodes(), associative.candidateNodes(), associative.nodeRatio(),
                associative.controlNanos() / 1_000_000L, associative.candidateNanos() / 1_000_000L,
                associative.timeRatio());

        List<Position> endgame = positions(24, 11, 80_921L);
        Comparison tail = compare(model, endgame, 11, ZebraSearchProfile.vanguardControl(),
                ZebraSearchProfile.vanguardTail(), true);
        System.out.printf("TAIL positions=%d scoreMismatches=%d moveMismatches=%d nodes=%d/%d ratio=%.4f time=%d/%dms ratio=%.4f%n",
                endgame.size(), tail.scoreMismatches(), tail.moveMismatches(), tail.controlNodes(),
                tail.candidateNodes(), tail.nodeRatio(), tail.controlNanos() / 1_000_000L,
                tail.candidateNanos() / 1_000_000L, tail.timeRatio());

        if (tail.scoreMismatches() != 0) {
            throw new IllegalStateException("Tail solver changed an exact score");
        }

        timedRegression(model);
        timedCorpus(model);
    }

    private static void timedCorpus(ZebraPositionEvaluator model) {
        List<Position> corpus = positions(12, 22, 319_027L);
        ZebraSearchEngine control = new ZebraSearchEngine(model, 21, ZebraSearchProfile.vanguardControl());
        ZebraSearchEngine candidate = new ZebraSearchEngine(model, 21, ZebraSearchProfile.vanguard());
        long controlMilliseconds = 0L;
        long candidateMilliseconds = 0L;
        int controlSolved = 0;
        int candidateSolved = 0;
        int solvedOutcomeMismatches = 0;
        for (int index = 0; index < corpus.size(); index++) {
            Position position = corpus.get(index);
            control.resetCaches();
            candidate.resetCaches();
            ZebraSearchResult controlResult;
            ZebraSearchResult candidateResult;
            if ((index & 1) == 0) {
                controlResult = control.findBestMove(position.player(), position.opponent(), 2_200L, 2_350L);
                candidateResult = candidate.findBestMove(position.player(), position.opponent(), 2_200L, 2_350L);
            } else {
                candidateResult = candidate.findBestMove(position.player(), position.opponent(), 2_200L, 2_350L);
                controlResult = control.findBestMove(position.player(), position.opponent(), 2_200L, 2_350L);
            }
            controlMilliseconds += controlResult.elapsedMilliseconds();
            candidateMilliseconds += candidateResult.elapsedMilliseconds();
            controlSolved += controlResult.outcomeSolved() ? 1 : 0;
            candidateSolved += candidateResult.outcomeSolved() ? 1 : 0;
            if (controlResult.outcomeSolved() && candidateResult.outcomeSolved()
                    && Integer.signum(controlResult.score()) != Integer.signum(candidateResult.score())) {
                solvedOutcomeMismatches++;
            }
            System.out.printf("WLD22_CASE index=%d solved=%s/%s time=%d/%dms move=%d/%d score=%d/%d%n",
                    index, controlResult.outcomeSolved(), candidateResult.outcomeSolved(),
                    controlResult.elapsedMilliseconds(), candidateResult.elapsedMilliseconds(),
                    controlResult.move(), candidateResult.move(), controlResult.score(), candidateResult.score());
        }
        System.out.printf("WLD22_CORPUS positions=%d solved=%d/%d time=%d/%dms solvedOutcomeMismatches=%d%n",
                corpus.size(), controlSolved, candidateSolved, controlMilliseconds, candidateMilliseconds,
                solvedOutcomeMismatches);
    }

    private static void timedRegression(ZebraPositionEvaluator model) {
        long player = 0x0405061E3B230000L;
        long opponent = 0x0000F8E0C49C7C7CL;
        ZebraSearchProfile[] profiles = {
                ZebraSearchProfile.vanguardControl(), ZebraSearchProfile.vanguardAssociative(),
                ZebraSearchProfile.vanguardTail(), ZebraSearchProfile.vanguard()
        };
        String[] names = {"control", "associative", "tail", "combined"};
        ZebraSearchEngine[] engines = new ZebraSearchEngine[profiles.length];
        long[] nodes = new long[profiles.length];
        long[] milliseconds = new long[profiles.length];
        for (int profile = 0; profile < profiles.length; profile++) {
            engines[profile] = new ZebraSearchEngine(model, 21, profiles[profile]);
            ZebraSearchResult warmup = engines[profile].findBestMove(player, opponent, 2_200L, 2_350L);
            requireWinningMove(warmup, names[profile]);
        }
        for (int repetition = 0; repetition < 6; repetition++) {
            for (int offset = 0; offset < profiles.length; offset++) {
                int profile = (repetition + offset) % profiles.length;
                engines[profile].resetCaches();
                ZebraSearchResult result = engines[profile].findBestMove(player, opponent, 2_200L, 2_350L);
                requireWinningMove(result, names[profile]);
                nodes[profile] += result.nodes();
                milliseconds[profile] += result.elapsedMilliseconds();
            }
        }
        for (int profile = 0; profile < profiles.length; profile++) {
            System.out.printf("WLD22 profile=%s averageNodes=%d averageTime=%.1fms%n", names[profile],
                    nodes[profile] / 6L, milliseconds[profile] / 6.0);
        }
    }

    private static void requireWinningMove(ZebraSearchResult result, String name) {
        if (result.move() != 7 || result.score() <= 30_000 || !result.outcomeSolved()) {
            throw new IllegalStateException(name + " failed the recorded WLD position: " + result);
        }
    }

    private static Comparison compare(ZebraPositionEvaluator model, List<Position> positions, int depth,
                                      ZebraSearchProfile controlProfile, ZebraSearchProfile candidateProfile,
                                      boolean alternateOrder) {
        long controlNodes = 0L;
        long candidateNodes = 0L;
        long controlNanos = 0L;
        long candidateNanos = 0L;
        int scoreMismatches = 0;
        int moveMismatches = 0;
        for (int index = 0; index < positions.size(); index++) {
            Position position = positions.get(index);
            ZebraSearchEngine control = new ZebraSearchEngine(model, 18, controlProfile);
            ZebraSearchEngine candidate = new ZebraSearchEngine(model, 18, candidateProfile);
            ZebraSearchResult controlResult;
            ZebraSearchResult candidateResult;
            long started;
            if (alternateOrder && (index & 1) != 0) {
                started = System.nanoTime();
                candidateResult = candidate.findBestMoveAtDepth(position.player(), position.opponent(), depth);
                candidateNanos += System.nanoTime() - started;
                started = System.nanoTime();
                controlResult = control.findBestMoveAtDepth(position.player(), position.opponent(), depth);
                controlNanos += System.nanoTime() - started;
            } else {
                started = System.nanoTime();
                controlResult = control.findBestMoveAtDepth(position.player(), position.opponent(), depth);
                controlNanos += System.nanoTime() - started;
                started = System.nanoTime();
                candidateResult = candidate.findBestMoveAtDepth(position.player(), position.opponent(), depth);
                candidateNanos += System.nanoTime() - started;
            }
            controlNodes += controlResult.nodes();
            candidateNodes += candidateResult.nodes();
            if (controlResult.score() != candidateResult.score()) {
                scoreMismatches++;
            }
            if (controlResult.move() != candidateResult.move()) {
                moveMismatches++;
            }
        }
        return new Comparison(controlNodes, candidateNodes, controlNanos, candidateNanos,
                scoreMismatches, moveMismatches);
    }

    private static List<Position> positions(int count, int targetEmpties, long seed) {
        Random random = new Random(seed);
        List<Position> result = new ArrayList<>(count);
        while (result.size() < count) {
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
                int selected = random.nextInt(Long.bitCount(legal));
                while (selected-- > 0) {
                    legal &= legal - 1L;
                }
                int move = Long.numberOfTrailingZeros(legal);
                long flipped = ZebraBitBoard.flips(player, opponent, move);
                long nextPlayer = opponent ^ flipped;
                long nextOpponent = player | flipped | (1L << move);
                player = nextPlayer;
                opponent = nextOpponent;
            }
            if (64 - Long.bitCount(player | opponent) == targetEmpties
                    && ZebraBitBoard.legalMoves(player, opponent) != 0L) {
                result.add(new Position(player, opponent));
            }
        }
        return result;
    }

    private record Position(long player, long opponent) {
    }

    private record Comparison(long controlNodes, long candidateNodes, long controlNanos, long candidateNanos,
                              int scoreMismatches, int moveMismatches) {
        private double nodeRatio() {
            return candidateNodes / (double) controlNodes;
        }

        private double timeRatio() {
            return candidateNanos / (double) controlNanos;
        }
    }
}
