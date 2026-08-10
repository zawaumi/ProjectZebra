package com.zawa.client.ai.zebra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class ZebraPonderEngine {
    private static final long PONDER_LIMIT_MILLISECONDS = 60_000L;

    private final ZebraVanguardPatternModel model;
    private final int workerCount;
    private final ExecutorService executor;
    private final ThreadLocal<ZebraSearchEngine> workerSearch;
    private final Map<PositionKey, ZebraSearchResult> results = new ConcurrentHashMap<>();
    private final List<Future<?>> tasks = new ArrayList<>();
    private final AtomicLong generation = new AtomicLong();

    ZebraPonderEngine(ZebraVanguardPatternModel model) {
        this.model = model;
        workerCount = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "zebra-ponder-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        };
        executor = Executors.newFixedThreadPool(workerCount, threadFactory);
        workerSearch = ThreadLocal.withInitial(
                () -> new ZebraSearchEngine(this.model, 18, ZebraSearchProfile.ponder()));
    }

    synchronized void start(long opponent, long player) {
        cancelTasks();
        results.clear();
        long currentGeneration = generation.incrementAndGet();
        long legal = ZebraBitBoard.legalMoves(opponent, player);
        int policyMove = ZebraOpeningPolicy.defaultPolicy().find(opponent, player);
        int[] moves = new int[40];
        int[] scores = new int[40];
        int count = 0;
        while (legal != 0L) {
            int move = Long.numberOfTrailingZeros(legal);
            legal &= legal - 1L;
            int score = replyPriority(opponent, player, move, policyMove);
            int insertion = count;
            while (insertion > 0 && scores[insertion - 1] < score) {
                scores[insertion] = scores[insertion - 1];
                moves[insertion] = moves[insertion - 1];
                insertion--;
            }
            scores[insertion] = score;
            moves[insertion] = move;
            count++;
        }
        for (int index = 0; index < Math.min(workerCount, count); index++) {
            submitReply(opponent, player, moves[index], currentGeneration);
        }
    }

    synchronized ZebraSearchResult stopAndFind(long player, long opponent) {
        cancelTasks();
        PositionKey key = new PositionKey(player, opponent);
        long deadline = System.nanoTime() + 25_000_000L;
        ZebraSearchResult result;
        while ((result = results.get(key)) == null && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        return result;
    }

    synchronized void stop() {
        generation.incrementAndGet();
        cancelTasks();
        results.clear();
    }

    private void submitReply(long opponent, long player, int move, long expectedGeneration) {
        long flipped = ZebraBitBoard.flips(opponent, player, move);
        long nextPlayer = player ^ flipped;
        long nextOpponent = opponent | flipped | (1L << move);
        PositionKey key = new PositionKey(nextPlayer, nextOpponent);
        tasks.add(executor.submit(() -> {
            ZebraSearchResult result = workerSearch.get().findBestMove(nextPlayer, nextOpponent,
                    PONDER_LIMIT_MILLISECONDS, PONDER_LIMIT_MILLISECONDS);
            int empties = 64 - Long.bitCount(nextPlayer | nextOpponent);
            boolean requiresResolution = empties >= 23 && empties <= 24;
            if (generation.get() == expectedGeneration && result.move() >= 0
                    && (!requiresResolution || result.outcomeSolved())) {
                results.put(key, result);
            }
        }));
    }

    private static int replyPriority(long opponent, long player, int move, int policyMove) {
        long flipped = ZebraBitBoard.flips(opponent, player, move);
        long nextPlayer = player ^ flipped;
        long nextOpponent = opponent | flipped | (1L << move);
        int priority = -Long.bitCount(ZebraBitBoard.legalMoves(nextPlayer, nextOpponent)) * 200;
        if (((1L << move) & ZebraBitBoard.CORNERS) != 0L) {
            priority += 20_000;
        }
        if (move == policyMove) {
            priority += 1_000_000;
        }
        return priority;
    }

    private void cancelTasks() {
        for (Future<?> task : tasks) {
            task.cancel(true);
        }
        tasks.clear();
    }

    private record PositionKey(long player, long opponent) {
    }
}
