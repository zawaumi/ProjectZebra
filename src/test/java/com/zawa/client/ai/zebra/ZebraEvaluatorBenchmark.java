package com.zawa.client.ai.zebra;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.SplittableRandom;

public final class ZebraEvaluatorBenchmark {
    private static final int POSITIONS = 4_096;
    private static final int ROUNDS = 256;

    private ZebraEvaluatorBenchmark() {
    }

    public static void main(String[] arguments) throws Throwable {
        ZebraVanguardPatternModel vanguard = ZebraVanguardPatternModel.loadDefault();
        MethodHandle teacher = MethodHandles.publicLookup().findStatic(Class.forName("othello.PatternEval"),
                "evaluateV2", MethodType.methodType(int.class, long.class, long.class));
        long[][] positions = positions();
        for (int warmup = 0; warmup < 8; warmup++) {
            runVanguard(vanguard, positions);
            runTeacher(teacher, positions);
        }
        Result vanguardResult = measureVanguard(vanguard, positions);
        Result teacherResult = measureTeacher(teacher, positions);
        if (vanguardResult.checksum() != teacherResult.checksum()) {
            throw new IllegalStateException("Evaluator checksums differ");
        }
        System.out.printf("vanguard ns/eval=%.1f teacher ns/eval=%.1f ratio=%.3f checksum=%d%n",
                vanguardResult.nanosecondsPerEvaluation(), teacherResult.nanosecondsPerEvaluation(),
                vanguardResult.nanosecondsPerEvaluation() / teacherResult.nanosecondsPerEvaluation(),
                vanguardResult.checksum());
    }

    private static Result measureVanguard(ZebraVanguardPatternModel model, long[][] positions) {
        long started = System.nanoTime();
        int checksum = runVanguard(model, positions);
        return new Result(checksum, (System.nanoTime() - started) / (double) (POSITIONS * ROUNDS));
    }

    private static Result measureTeacher(MethodHandle teacher, long[][] positions) throws Throwable {
        long started = System.nanoTime();
        int checksum = runTeacher(teacher, positions);
        return new Result(checksum, (System.nanoTime() - started) / (double) (POSITIONS * ROUNDS));
    }

    private static int runVanguard(ZebraVanguardPatternModel model, long[][] positions) {
        int checksum = 0;
        for (int round = 0; round < ROUNDS; round++) {
            for (long[] position : positions) {
                checksum = Integer.rotateLeft(checksum, 1) ^ model.evaluate(position[0], position[1]);
            }
        }
        return checksum;
    }

    private static int runTeacher(MethodHandle teacher, long[][] positions) throws Throwable {
        int checksum = 0;
        for (int round = 0; round < ROUNDS; round++) {
            for (long[] position : positions) {
                int score = (int) teacher.invokeExact(position[0], position[1]);
                checksum = Integer.rotateLeft(checksum, 1) ^ score;
            }
        }
        return checksum;
    }

    private static long[][] positions() {
        long[][] result = new long[POSITIONS][2];
        SplittableRandom random = new SplittableRandom(20260722L);
        long player = ZebraBitBoard.INITIAL_BLACK;
        long opponent = ZebraBitBoard.INITIAL_WHITE;
        for (int index = 0; index < result.length; index++) {
            result[index][0] = player;
            result[index][1] = opponent;
            long legal = ZebraBitBoard.legalMoves(player, opponent);
            if (legal == 0L) {
                long swap = player;
                player = opponent;
                opponent = swap;
                legal = ZebraBitBoard.legalMoves(player, opponent);
                if (legal == 0L) {
                    player = ZebraBitBoard.INITIAL_BLACK;
                    opponent = ZebraBitBoard.INITIAL_WHITE;
                    continue;
                }
            }
            int selected = random.nextInt(Long.bitCount(legal));
            while (selected-- > 0) {
                legal &= legal - 1L;
            }
            int move = Long.numberOfTrailingZeros(legal);
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            long nextPlayer = opponent ^ flipped;
            opponent = player | flipped | (1L << move);
            player = nextPlayer;
        }
        return result;
    }

    private record Result(int checksum, double nanosecondsPerEvaluation) {
    }
}
