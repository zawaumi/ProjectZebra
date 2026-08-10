package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraVanguardPatternModel;
import com.zawa.client.ai.zebra.ZebraVanguardResidualModel;

import java.nio.file.Path;

record ZebraVanguardTrainingConfig(Path teacher, Path output, Path residualOutput,
                                   int samplesPerStage, int distillationDepth, long seed) {
    static boolean requested(String[] arguments) {
        for (String argument : arguments) {
            if (argument.startsWith("--teacher=")) {
                return true;
            }
        }
        return false;
    }

    static ZebraVanguardTrainingConfig parse(String[] arguments) {
        Path teacher = null;
        Path output = Path.of(ZebraVanguardPatternModel.DEFAULT_FILE_NAME);
        Path residualOutput = Path.of(ZebraVanguardResidualModel.DEFAULT_FILE_NAME);
        int samplesPerStage = 2_000;
        int distillationDepth = 2;
        long seed = 0x56414E4755415244L;
        for (String argument : arguments) {
            if (argument.startsWith("--teacher=")) {
                teacher = Path.of(argument.substring("--teacher=".length()));
            } else if (argument.startsWith("--output=")) {
                output = Path.of(argument.substring("--output=".length()));
            } else if (argument.startsWith("--residual-output=")) {
                residualOutput = Path.of(argument.substring("--residual-output=".length()));
            } else if (argument.startsWith("--samples-per-stage=")) {
                samplesPerStage = Integer.parseInt(argument.substring("--samples-per-stage=".length()));
            } else if (argument.startsWith("--distillation-depth=")) {
                distillationDepth = Integer.parseInt(argument.substring("--distillation-depth=".length()));
            } else if (argument.startsWith("--seed=")) {
                seed = Long.parseLong(argument.substring("--seed=".length()));
            } else if (!argument.equals("--help")) {
                throw new IllegalArgumentException("Unknown Vanguard trainer argument: " + argument);
            }
        }
        if (teacher == null) {
            throw new IllegalArgumentException("--teacher=PATH is required");
        }
        if (samplesPerStage < 100 || distillationDepth < 1 || distillationDepth > 4) {
            throw new IllegalArgumentException("samples-per-stage must be >= 100 and distillation-depth must be 1..4");
        }
        return new ZebraVanguardTrainingConfig(teacher, output, residualOutput,
                samplesPerStage, distillationDepth, seed);
    }
}
