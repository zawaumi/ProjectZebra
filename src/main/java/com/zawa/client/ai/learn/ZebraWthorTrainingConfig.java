package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraOpeningPolicy;

import java.nio.file.Path;

record ZebraWthorTrainingConfig(Path input, Path output, int maximumPly,
                                int minimumVisits, double minimumShare) {
    static boolean requested(String[] arguments) {
        for (String argument : arguments) {
            if (argument.startsWith("--wthor=")) {
                return true;
            }
        }
        return false;
    }

    static ZebraWthorTrainingConfig parse(String[] arguments) {
        Path input = null;
        Path output = Path.of(ZebraOpeningPolicy.DEFAULT_FILE_NAME);
        int maximumPly = 20;
        int minimumVisits = 12;
        double minimumShare = 0.20;
        for (String argument : arguments) {
            if (argument.startsWith("--wthor=")) {
                input = Path.of(argument.substring("--wthor=".length()));
            } else if (argument.startsWith("--output=")) {
                output = Path.of(argument.substring("--output=".length()));
            } else if (argument.startsWith("--maximum-ply=")) {
                maximumPly = Integer.parseInt(argument.substring("--maximum-ply=".length()));
            } else if (argument.startsWith("--minimum-visits=")) {
                minimumVisits = Integer.parseInt(argument.substring("--minimum-visits=".length()));
            } else if (argument.startsWith("--minimum-share=")) {
                minimumShare = Double.parseDouble(argument.substring("--minimum-share=".length()));
            } else if (!argument.equals("--help")) {
                throw new IllegalArgumentException("Unknown WTHOR trainer argument: " + argument);
            }
        }
        if (input == null) {
            throw new IllegalArgumentException("--wthor=PATH is required");
        }
        if (maximumPly < 1 || maximumPly > 40 || minimumVisits < 2
                || minimumShare <= 0.0 || minimumShare > 1.0) {
            throw new IllegalArgumentException("invalid WTHOR policy limits");
        }
        return new ZebraWthorTrainingConfig(input, output, maximumPly, minimumVisits, minimumShare);
    }
}
