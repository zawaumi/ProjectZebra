package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraBitBoard;
import com.zawa.client.ai.zebra.ZebraSearchEngine;
import com.zawa.client.ai.zebra.ZebraSearchProfile;
import com.zawa.client.ai.zebra.ZebraSearchResult;
import com.zawa.client.ai.zebra.ZebraStrategicFeatures;
import com.zawa.client.ai.zebra.ZebraVanguardPatternModel;
import com.zawa.client.ai.zebra.ZebraVanguardResidualModel;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

final class ZebraVanguardResidualTrainer {
    private static final int MAXIMUM_ADJUSTMENT = 1_200;
    private static final double RIDGE_PENALTY = 12.0;
    private static final int MAXIMUM_LABEL = 4_000;

    private ZebraVanguardResidualTrainer() {
    }

    static void train(ZebraVanguardTrainingConfig config, int stages) throws IOException {
        ZebraVanguardPatternModel model = ZebraVanguardPatternModel.load(config.output());
        ZebraSearchEngine teacherSearch = new ZebraSearchEngine(model, 18, ZebraSearchProfile.vanguard());
        List<Sample>[] samples = collect(config, stages, model, teacherSearch);
        float[][] weights = fit(samples, stages);
        Validation validation = validate(samples, weights);
        write(config.residualOutput(), weights);
        System.out.printf("Vanguard search distillation written: %s samples=%d depth=%d baseRMSE=%.2f distilledRMSE=%.2f%n",
                config.residualOutput().toAbsolutePath(), validation.samples(), config.distillationDepth(),
                validation.baseRmse(), validation.distilledRmse());
    }

    @SuppressWarnings("unchecked")
    private static List<Sample>[] collect(ZebraVanguardTrainingConfig config, int stages,
                                          ZebraVanguardPatternModel model, ZebraSearchEngine search) {
        List<Sample>[] samples = new List[stages];
        for (int stage = 0; stage < stages; stage++) {
            samples[stage] = new ArrayList<>(config.samplesPerStage());
        }
        SplittableRandom random = new SplittableRandom(config.seed());
        int remaining = stages * config.samplesPerStage();
        int game = 0;
        while (remaining > 0) {
            long black = ZebraBitBoard.INITIAL_BLACK;
            long white = ZebraBitBoard.INITIAL_WHITE;
            boolean blackToMove = true;
            int passes = 0;
            while (passes < 2) {
                long player = blackToMove ? black : white;
                long opponent = blackToMove ? white : black;
                long legal = ZebraBitBoard.legalMoves(player, opponent);
                if (legal == 0L) {
                    passes++;
                    blackToMove = !blackToMove;
                    continue;
                }
                passes = 0;
                int discs = Long.bitCount(player | opponent);
                int stage = Math.max(0, Math.min(stages - 1, (discs - 13) >> 2));
                if (discs >= 10 && samples[stage].size() < config.samplesPerStage()) {
                    int base = model.evaluate(player, opponent, legal);
                    ZebraSearchResult result = search.findBestMoveAtDepth(player, opponent,
                            Math.min(config.distillationDepth(), 64 - discs));
                    int correction = Math.max(-MAXIMUM_LABEL, Math.min(MAXIMUM_LABEL, result.score() - base));
                    double[] features = new double[ZebraStrategicFeatures.COUNT];
                    ZebraStrategicFeatures.extract(player, opponent, legal, base, features);
                    samples[stage].add(new Sample(features, correction));
                    remaining--;
                }
                int move = randomMove(legal, random);
                long flipped = ZebraBitBoard.flips(player, opponent, move);
                player |= flipped | (1L << move);
                opponent ^= flipped;
                if (blackToMove) {
                    black = player;
                    white = opponent;
                } else {
                    white = player;
                    black = opponent;
                }
                blackToMove = !blackToMove;
            }
            game++;
            if (game > config.samplesPerStage() * 20 && remaining > 0) {
                throw new IllegalStateException("could not collect balanced distillation positions");
            }
        }
        return samples;
    }

    private static float[][] fit(List<Sample>[] samples, int stages) {
        float[][] result = new float[stages][ZebraStrategicFeatures.COUNT];
        for (int stage = 0; stage < stages; stage++) {
            double[][] matrix = new double[ZebraStrategicFeatures.COUNT][ZebraStrategicFeatures.COUNT];
            double[] vector = new double[ZebraStrategicFeatures.COUNT];
            List<Sample> stageSamples = samples[stage];
            for (int sampleIndex = 0; sampleIndex < stageSamples.size(); sampleIndex++) {
                if (isValidation(sampleIndex)) {
                    continue;
                }
                Sample sample = stageSamples.get(sampleIndex);
                for (int row = 0; row < matrix.length; row++) {
                    vector[row] += sample.features()[row] * sample.correction();
                    for (int column = 0; column < matrix.length; column++) {
                        matrix[row][column] += sample.features()[row] * sample.features()[column];
                    }
                }
            }
            for (int feature = 0; feature < matrix.length; feature++) {
                matrix[feature][feature] += RIDGE_PENALTY;
            }
            double[] stageWeights = solve(matrix, vector);
            for (int feature = 0; feature < stageWeights.length; feature++) {
                result[stage][feature] = (float) stageWeights[feature];
            }
        }
        return result;
    }

    private static Validation validate(List<Sample>[] samples, float[][] weights) {
        double baseSquaredError = 0.0;
        double distilledSquaredError = 0.0;
        int count = 0;
        for (int stage = 0; stage < samples.length; stage++) {
            List<Sample> stageSamples = samples[stage];
            for (int sampleIndex = 0; sampleIndex < stageSamples.size(); sampleIndex++) {
                if (!isValidation(sampleIndex)) {
                    continue;
                }
                Sample sample = stageSamples.get(sampleIndex);
                double prediction = 0.0;
                for (int feature = 0; feature < weights[stage].length; feature++) {
                    prediction += weights[stage][feature] * sample.features()[feature];
                }
                prediction = Math.max(-MAXIMUM_ADJUSTMENT, Math.min(MAXIMUM_ADJUSTMENT, prediction));
                baseSquaredError += (double) sample.correction() * sample.correction();
                double error = sample.correction() - prediction;
                distilledSquaredError += error * error;
                count++;
            }
        }
        return new Validation(count, Math.sqrt(baseSquaredError / count),
                Math.sqrt(distilledSquaredError / count));
    }

    private static double[] solve(double[][] matrix, double[] vector) {
        int size = vector.length;
        double[][] augmented = new double[size][size + 1];
        for (int row = 0; row < size; row++) {
            System.arraycopy(matrix[row], 0, augmented[row], 0, size);
            augmented[row][size] = vector[row];
        }
        for (int pivot = 0; pivot < size; pivot++) {
            int strongest = pivot;
            for (int row = pivot + 1; row < size; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[strongest][pivot])) {
                    strongest = row;
                }
            }
            double[] temporary = augmented[pivot];
            augmented[pivot] = augmented[strongest];
            augmented[strongest] = temporary;
            double divisor = augmented[pivot][pivot];
            if (Math.abs(divisor) < 1.0e-12) {
                continue;
            }
            for (int column = pivot; column <= size; column++) {
                augmented[pivot][column] /= divisor;
            }
            for (int row = 0; row < size; row++) {
                if (row == pivot) {
                    continue;
                }
                double factor = augmented[row][pivot];
                for (int column = pivot; column <= size; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }
        double[] result = new double[size];
        for (int row = 0; row < size; row++) {
            result[row] = augmented[row][size];
        }
        return result;
    }

    private static void write(Path path, float[][] weights) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(ZebraVanguardResidualModel.MAGIC);
            output.writeInt(ZebraVanguardResidualModel.VERSION);
            output.writeInt(weights.length);
            output.writeInt(ZebraStrategicFeatures.COUNT);
            output.writeInt(MAXIMUM_ADJUSTMENT);
            for (float[] stageWeights : weights) {
                for (float weight : stageWeights) {
                    output.writeFloat(weight);
                }
            }
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isValidation(int index) {
        return index % 5 == 0;
    }

    private static int randomMove(long legal, SplittableRandom random) {
        int selected = random.nextInt(Long.bitCount(legal));
        while (selected-- > 0) {
            legal &= legal - 1L;
        }
        return Long.numberOfTrailingZeros(legal);
    }

    private record Sample(double[] features, int correction) {
    }

    private record Validation(int samples, double baseRmse, double distilledRmse) {
    }
}
