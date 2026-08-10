package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraPatternModel;

import java.nio.file.Path;

public record ZebraTrainingConfig(int games, int threads, int logInterval, int saveInterval, Path input,
                                  Path output, long seed, long initialGames) {
    static ZebraTrainingConfig parse(String[] arguments) {
        int games = 0;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int logInterval = 100;
        int saveInterval = 1_000;
        Path output = Path.of(ZebraPatternModel.DEFAULT_FILE_NAME);
        Path input = null;
        long seed = 2026L;
        long initialGames = -1L;
        for (String argument : arguments) {
            if (argument.startsWith("--games=")) {
                games = nonNegative(argument, "--games=");
            } else if (argument.startsWith("--threads=")) {
                threads = positive(argument, "--threads=");
            } else if (argument.startsWith("--log-interval=")) {
                logInterval = positive(argument, "--log-interval=");
            } else if (argument.startsWith("--save-interval=")) {
                saveInterval = positive(argument, "--save-interval=");
            } else if (argument.startsWith("--output=")) {
                output = Path.of(argument.substring("--output=".length()));
            } else if (argument.startsWith("--input=")) {
                input = Path.of(argument.substring("--input=".length()));
            } else if (argument.startsWith("--seed=")) {
                seed = Long.parseLong(argument.substring("--seed=".length()));
            } else if (argument.startsWith("--initial-games=")) {
                initialGames = Long.parseLong(argument.substring("--initial-games=".length()));
                if (initialGames < 0L) {
                    throw new IllegalArgumentException("--initial-games= requires a non-negative integer");
                }
            } else if (argument.equals("--help")) {
                printUsageAndExit();
            } else {
                throw new IllegalArgumentException("Unknown trainer argument: " + argument);
            }
        }
        return new ZebraTrainingConfig(games, threads, logInterval, saveInterval,
                input == null ? output : input, output, seed, initialGames);
    }

    private static int positive(String argument, String prefix) {
        int value = Integer.parseInt(argument.substring(prefix.length()));
        if (value <= 0) {
            throw new IllegalArgumentException(prefix + " requires a positive integer");
        }
        return value;
    }

    private static int nonNegative(String argument, String prefix) {
        int value = Integer.parseInt(argument.substring(prefix.length()));
        if (value < 0) {
            throw new IllegalArgumentException(prefix + " requires a non-negative integer");
        }
        return value;
    }

    private static void printUsageAndExit() {
        System.out.println("OthelloTrainer [--games=N] [--threads=N] [--log-interval=N] [--save-interval=N] [--input=PATH] [--output=PATH] [--seed=N] [--initial-games=N]");
        System.exit(0);
    }
}
