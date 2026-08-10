package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraBitBoard;
import com.zawa.client.ai.zebra.ZebraPatternModel;
import com.zawa.client.ai.zebra.ZebraSearchEngine;
import com.zawa.client.ai.zebra.ZebraSearchResult;

import java.io.IOException;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ZebraSelfPlayTrainer {
    private static final double TRACE_DECAY = 0.72;

    private final ZebraPatternModel model;
    private final ZebraTrainingConfig config;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger claimedGames = new AtomicInteger();
    private final AtomicInteger completedGames = new AtomicInteger();
    private final AtomicInteger blackWins = new AtomicInteger();
    private final AtomicInteger whiteWins = new AtomicInteger();
    private final AtomicInteger draws = new AtomicInteger();
    private final long started = System.nanoTime();
    private final long initialGames;

    public ZebraSelfPlayTrainer(ZebraPatternModel model, ZebraTrainingConfig config) {
        this.model = model;
        this.config = config;
        this.initialGames = model.trainedGames();
    }

    public void run() throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            saveModel();
        }, "zebra-trainer-shutdown"));
        System.out.printf("Zebra TCL self-play started: games=%s threads=%d output=%s%n",
                config.games() == 0 ? "continuous" : Integer.toString(config.games()), config.threads(), config.output());
        ExecutorService workers = Executors.newFixedThreadPool(config.threads());
        for (int worker = 0; worker < config.threads(); worker++) {
            long workerSeed = config.seed() + 0x9E3779B97F4A7C15L * worker;
            workers.submit(() -> trainWorker(workerSeed));
        }
        workers.shutdown();
        while (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
            if (!running.get()) {
                workers.shutdownNow();
            }
        }
        saveModel();
        printProgress(completedGames.get());
    }

    private void trainWorker(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        ZebraSearchEngine searchEngine = new ZebraSearchEngine(model, 15);
        while (running.get()) {
            int ticket = claimedGames.getAndIncrement();
            if (config.games() > 0 && ticket >= config.games()) {
                return;
            }
            long trainingStep = initialGames + ticket;
            int outcome = playAndLearn(searchEngine, random, trainingStep);
            if (outcome > 0) {
                blackWins.incrementAndGet();
            } else if (outcome < 0) {
                whiteWins.incrementAndGet();
            } else {
                draws.incrementAndGet();
            }
            int finished = completedGames.incrementAndGet();
            model.updateTrainedGames(initialGames + finished);
            if (finished % config.logInterval() == 0) {
                printProgress(finished);
            }
            if (finished % config.saveInterval() == 0) {
                saveModel();
            }
        }
    }

    private int playAndLearn(ZebraSearchEngine searchEngine, SplittableRandom random, long trainingStep) {
        searchEngine.resetCaches();
        long black = ZebraBitBoard.INITIAL_BLACK;
        long white = ZebraBitBoard.INITIAL_WHITE;
        int turn = 1;
        long[] blackHistory = new long[60];
        long[] whiteHistory = new long[60];
        int[] turnHistory = new int[60];
        int positions = 0;
        int searchDepth = progressiveDepth(trainingStep);
        double exploration = explorationRate(trainingStep);

        while (true) {
            long player = turn == 1 ? black : white;
            long opponent = turn == 1 ? white : black;
            long legal = ZebraBitBoard.legalMoves(player, opponent);
            if (legal == 0L) {
                turn = -turn;
                player = turn == 1 ? black : white;
                opponent = turn == 1 ? white : black;
                legal = ZebraBitBoard.legalMoves(player, opponent);
                if (legal == 0L) {
                    break;
                }
            }

            blackHistory[positions] = black;
            whiteHistory[positions] = white;
            turnHistory[positions] = turn;
            positions++;

            int move;
            double moveExploration = positions <= 10 ? Math.min(0.45, exploration * 1.6) : exploration;
            if (random.nextDouble() < moveExploration) {
                move = randomMove(legal, random);
            } else {
                ZebraSearchResult result = searchEngine.findBestMoveAtDepth(player, opponent, searchDepth);
                move = result.move();
                if (move < 0 || (legal & (1L << move)) == 0L) {
                    move = Long.numberOfTrailingZeros(legal);
                }
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

        int difference = Long.bitCount(black) - Long.bitCount(white);
        int outcome = Integer.compare(difference, 0);
        double[] absolutePredictions = new double[positions];
        for (int i = 0; i < positions; i++) {
            long player = turnHistory[i] == 1 ? blackHistory[i] : whiteHistory[i];
            long opponent = turnHistory[i] == 1 ? whiteHistory[i] : blackHistory[i];
            absolutePredictions[i] = turnHistory[i] * model.value(player, opponent);
        }

        double lambdaReturn = outcome;
        double learningRate = learningRate(trainingStep);
        for (int i = positions - 1; i >= 0; i--) {
            if (i + 1 < positions) {
                lambdaReturn = (1.0 - TRACE_DECAY) * absolutePredictions[i + 1] + TRACE_DECAY * lambdaReturn;
            }
            double relativeTarget = turnHistory[i] * lambdaReturn;
            for (int transform = 0; transform < 8; transform++) {
                long transformedBlack = ZebraBitBoard.transform(blackHistory[i], transform);
                long transformedWhite = ZebraBitBoard.transform(whiteHistory[i], transform);
                long player = turnHistory[i] == 1 ? transformedBlack : transformedWhite;
                long opponent = turnHistory[i] == 1 ? transformedWhite : transformedBlack;
                model.learn(player, opponent, relativeTarget, learningRate);
            }
        }
        return outcome;
    }

    private void printProgress(int games) {
        double seconds = Math.max(0.001, (System.nanoTime() - started) / 1_000_000_000.0);
        long totalGames = initialGames + games;
        System.out.printf("games=%d total=%d rate=%.1f/s black=%d white=%d draw=%d depth=%d exploration=%.3f%n",
                games, totalGames, games / seconds, blackWins.get(), whiteWins.get(), draws.get(),
                progressiveDepth(totalGames), explorationRate(totalGames));
    }

    private void saveModel() {
        try {
            model.save(config.output());
        } catch (IOException exception) {
            System.err.println("Failed to save Zebra TCL model: " + exception.getMessage());
        }
    }

    private static int progressiveDepth(long games) {
        if (games < 500) {
            return 2;
        }
        if (games < 5_000) {
            return 3;
        }
        if (games < 30_000) {
            return 4;
        }
        return 5;
    }

    private static double explorationRate(long games) {
        return 0.03 + 0.22 * Math.exp(-games / 12_000.0);
    }

    private static double learningRate(long games) {
        return 0.0025 / Math.sqrt(1.0 + games / 20_000.0);
    }

    private static int randomMove(long legal, SplittableRandom random) {
        int selected = random.nextInt(Long.bitCount(legal));
        long remaining = legal;
        while (selected-- > 0) {
            remaining &= remaining - 1L;
        }
        return Long.numberOfTrailingZeros(remaining);
    }
}
