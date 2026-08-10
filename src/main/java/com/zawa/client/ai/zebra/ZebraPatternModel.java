package com.zawa.client.ai.zebra;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class ZebraPatternModel implements ZebraPositionEvaluator {
    public static final String DEFAULT_FILE_NAME = "zebra_tcl_weights.bin";
    public static final int PHASE_COUNT = 12;
    public static final int FEATURE_COUNT = 10;
    public static final int PATTERN_COUNT = 61;
    public static final double VALUE_SCALE = 4_000.0;

    private static final int MAGIC = 0x5A54434C;
    private static final int VERSION = 2;
    private static final int LOCAL_PATTERN_SIZE = 81;
    private static final int LOCAL_PATTERN_COUNT = 49;
    private static final int EDGE_PATTERN_SIZE = 6_561;
    private static final int CORNER_PATTERN_SIZE = 729;
    private static final int EDGE_OFFSET = LOCAL_PATTERN_COUNT * LOCAL_PATTERN_SIZE;
    private static final int CORNER_OFFSET = EDGE_OFFSET + EDGE_PATTERN_SIZE;
    private static final int PHASE_STRIDE = CORNER_OFFSET + CORNER_PATTERN_SIZE;

    private static final long[] EMPTY_CORNER_NEIGHBORS = {
            0x0000000000000302L,
            0x000000000000C040L,
            0x0203000000000000L,
            0x40C0000000000000L
    };
    private static final long[] CORNER_BITS = {
            0x0000000000000001L,
            0x0000000000000080L,
            0x0100000000000000L,
            0x8000000000000000L
    };
    private static final int[][] EDGE_PATTERNS = {
            {0, 1, 2, 3, 4, 5, 6, 7},
            {7, 15, 23, 31, 39, 47, 55, 63},
            {63, 62, 61, 60, 59, 58, 57, 56},
            {56, 48, 40, 32, 24, 16, 8, 0}
    };
    private static final int[][] CORNER_PATTERNS = buildCornerPatterns();
    private static final int[] FEATURE_SCALES = {20, 32, 32, 4, 12, 24, 64, 24, 1, 1};
    private static final int[] POSITION_VALUES = {
            90, -28, 10, 6, 6, 10, -28, 90,
            -28, -42, -5, -4, -4, -5, -42, -28,
            10, -5, 4, 2, 2, 4, -5, 10,
            6, -4, 2, 1, 1, 2, -4, 6,
            6, -4, 2, 1, 1, 2, -4, 6,
            10, -5, 4, 2, 2, 4, -5, 10,
            -28, -42, -5, -4, -4, -5, -42, -28,
            90, -28, 10, 6, 6, 10, -28, 90
    };

    private final float[] patternWeights;
    private final float[] linearWeights;
    private final float[] temporalSums;
    private final float[] temporalAbsoluteSums;
    private final ThreadLocal<int[]> patternBuffer = ThreadLocal.withInitial(() -> new int[PATTERN_COUNT]);
    private final ThreadLocal<int[]> featureBuffer = ThreadLocal.withInitial(() -> new int[FEATURE_COUNT]);
    private long trainedGames;

    private ZebraPatternModel() {
        patternWeights = new float[PHASE_COUNT * PHASE_STRIDE];
        linearWeights = new float[PHASE_COUNT * FEATURE_COUNT];
        temporalSums = new float[patternWeights.length + linearWeights.length];
        temporalAbsoluteSums = new float[temporalSums.length];
        initializeDefaults();
    }

    public static ZebraPatternModel loadDefault() {
        ZebraPatternModel model = new ZebraPatternModel();
        Path external = Path.of(DEFAULT_FILE_NAME);
        if (Files.isRegularFile(external) && model.loadFromPath(external)) {
            return model;
        }
        try (InputStream resource = ZebraPatternModel.class.getResourceAsStream("/" + DEFAULT_FILE_NAME)) {
            if (resource != null) {
                model.read(resource);
            }
        } catch (IOException ignored) {
        }
        return model;
    }

    public static ZebraPatternModel load(Path path) {
        ZebraPatternModel model = new ZebraPatternModel();
        if (Files.isRegularFile(path)) {
            model.loadFromPath(path);
        }
        return model;
    }

    public int evaluate(long player, long opponent) {
        return evaluate(player, opponent, ZebraBitBoard.legalMoves(player, opponent));
    }

    @Override
    public int evaluate(long player, long opponent, long playerMoves) {
        int occupied = Long.bitCount(player | opponent);
        int phase = phase(occupied);
        int[] indexes = patternBuffer.get();
        encodePatterns(player, opponent, phase, indexes);
        float score = 0.0f;
        for (int index : indexes) {
            score += patternWeights[index];
        }
        int[] features = featureBuffer.get();
        extractFeatures(player, opponent, playerMoves, features);
        int linearOffset = phase * FEATURE_COUNT;
        for (int i = 0; i < FEATURE_COUNT; i++) {
            score += linearWeights[linearOffset + i] * features[i];
        }
        return Math.max(-20_000, Math.min(20_000, Math.round(score)));
    }

    public double value(long player, long opponent) {
        return Math.tanh(evaluate(player, opponent) / VALUE_SCALE);
    }

    public synchronized long trainedGames() {
        return trainedGames;
    }

    public synchronized void setTrainedGames(long games) {
        trainedGames = Math.max(0L, games);
    }

    public synchronized void updateTrainedGames(long games) {
        trainedGames = Math.max(trainedGames, games);
    }

    public synchronized void learn(long player, long opponent, double target, double learningRate) {
        int occupied = Long.bitCount(player | opponent);
        int phase = phase(occupied);
        int[] indexes = patternBuffer.get();
        encodePatterns(player, opponent, phase, indexes);
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        int[] features = featureBuffer.get();
        extractFeatures(player, opponent, legal, features);
        double prediction = Math.tanh(evaluate(player, opponent, legal) / VALUE_SCALE);
        double derivative = (target - prediction) * (1.0 - prediction * prediction);
        float tupleGradient = (float) (learningRate * derivative * VALUE_SCALE / PATTERN_COUNT);
        for (int index : indexes) {
            applyTemporalUpdate(index, tupleGradient, patternWeights, 0, 600.0f);
        }
        int linearOffset = phase * FEATURE_COUNT;
        int temporalOffset = patternWeights.length;
        for (int i = 0; i < FEATURE_COUNT; i++) {
            float gradient = (float) (learningRate * derivative * VALUE_SCALE * features[i]
                    / (FEATURE_SCALES[i] * (double) FEATURE_COUNT));
            applyTemporalUpdate(linearOffset + i, gradient, linearWeights, temporalOffset, 3_000.0f);
        }
    }

    public synchronized void save(Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(PHASE_COUNT);
            output.writeInt(PHASE_STRIDE);
            output.writeInt(FEATURE_COUNT);
            output.writeLong(trainedGames);
            for (float weight : patternWeights) {
                output.writeFloat(weight);
            }
            for (float weight : linearWeights) {
                output.writeFloat(weight);
            }
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean loadFromPath(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            read(input);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private synchronized void read(InputStream source) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(source))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Unsupported Zebra TCL model");
            }
            int version = input.readInt();
            if (version != 1 && version != VERSION) {
                throw new IOException("Unsupported Zebra TCL model version");
            }
            if (input.readInt() != PHASE_COUNT || input.readInt() != PHASE_STRIDE || input.readInt() != FEATURE_COUNT) {
                throw new IOException("Incompatible Zebra TCL model dimensions");
            }
            trainedGames = version >= 2 ? input.readLong() : 0L;
            for (int i = 0; i < patternWeights.length; i++) {
                patternWeights[i] = input.readFloat();
            }
            for (int i = 0; i < linearWeights.length; i++) {
                linearWeights[i] = input.readFloat();
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing Zebra TCL model data");
            }
        } catch (EOFException exception) {
            throw new IOException("Truncated Zebra TCL model", exception);
        }
        Arrays.fill(temporalSums, 0.0f);
        Arrays.fill(temporalAbsoluteSums, 0.0f);
    }

    private void initializeDefaults() {
        int[] occurrences = new int[64];
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 7; column++) {
                occurrences[row * 8 + column]++;
                occurrences[row * 8 + column + 1]++;
                occurrences[(row + 1) * 8 + column]++;
                occurrences[(row + 1) * 8 + column + 1]++;
            }
        }
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
            float positionalScale = 2.5f - phase * 0.12f;
            for (int row = 0; row < 7; row++) {
                for (int column = 0; column < 7; column++) {
                    int pattern = row * 7 + column;
                    int[] squares = {
                            row * 8 + column,
                            row * 8 + column + 1,
                            (row + 1) * 8 + column,
                            (row + 1) * 8 + column + 1
                    };
                    for (int code = 0; code < LOCAL_PATTERN_SIZE; code++) {
                        int state = code;
                        float value = 0.0f;
                        for (int square : squares) {
                            int piece = state % 3;
                            state /= 3;
                            int sign = piece == 1 ? 1 : piece == 2 ? -1 : 0;
                            value += sign * POSITION_VALUES[square] * positionalScale / occurrences[square];
                        }
                        patternWeights[phase * PHASE_STRIDE + pattern * LOCAL_PATTERN_SIZE + code] = value;
                    }
                }
            }
            float progress = phase / (float) (PHASE_COUNT - 1);
            int offset = phase * FEATURE_COUNT;
            linearWeights[offset] = 95.0f - 35.0f * progress;
            linearWeights[offset + 1] = 24.0f - 12.0f * progress;
            linearWeights[offset + 2] = 30.0f - 15.0f * progress;
            linearWeights[offset + 3] = 1_450.0f;
            linearWeights[offset + 4] = 230.0f - 120.0f * progress;
            linearWeights[offset + 5] = 150.0f + 170.0f * progress;
            linearWeights[offset + 6] = -12.0f + 82.0f * progress * progress;
            linearWeights[offset + 7] = 20.0f + 20.0f * progress;
            linearWeights[offset + 8] = phase >= 8 ? 85.0f : 0.0f;
            linearWeights[offset + 9] = 35.0f;
        }
    }

    private void applyTemporalUpdate(int localIndex, float gradient, float[] weights, int temporalOffset, float limit) {
        int temporalIndex = temporalOffset + localIndex;
        temporalSums[temporalIndex] += gradient;
        temporalAbsoluteSums[temporalIndex] += Math.abs(gradient);
        float coherence = Math.abs(temporalSums[temporalIndex])
                / Math.max(temporalAbsoluteSums[temporalIndex], 1.0e-6f);
        weights[localIndex] = Math.max(-limit, Math.min(limit, weights[localIndex] + coherence * gradient));
    }

    private static int phase(int occupied) {
        return Math.min(PHASE_COUNT - 1, Math.max(0, (occupied - 4) * PHASE_COUNT / 61));
    }

    private static void encodePatterns(long player, long opponent, int phase, int[] destination) {
        int phaseOffset = phase * PHASE_STRIDE;
        int output = 0;
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 7; column++) {
                int first = row * 8 + column;
                int code = pieceAt(player, opponent, first)
                        + 3 * pieceAt(player, opponent, first + 1)
                        + 9 * pieceAt(player, opponent, first + 8)
                        + 27 * pieceAt(player, opponent, first + 9);
                destination[output++] = phaseOffset + (row * 7 + column) * LOCAL_PATTERN_SIZE + code;
            }
        }
        for (int[] pattern : EDGE_PATTERNS) {
            destination[output++] = phaseOffset + EDGE_OFFSET + encode(player, opponent, pattern);
        }
        for (int[] pattern : CORNER_PATTERNS) {
            destination[output++] = phaseOffset + CORNER_OFFSET + encode(player, opponent, pattern);
        }
    }

    private static int encode(long player, long opponent, int[] pattern) {
        int code = 0;
        int multiplier = 1;
        for (int square : pattern) {
            code += pieceAt(player, opponent, square) * multiplier;
            multiplier *= 3;
        }
        return code;
    }

    private static int pieceAt(long player, long opponent, int square) {
        long bit = 1L << square;
        if ((player & bit) != 0L) {
            return 1;
        }
        return (opponent & bit) != 0L ? 2 : 0;
    }

    private static void extractFeatures(long player, long opponent, long playerMoves, int[] features) {
        long occupied = player | opponent;
        long empty = ~occupied;
        long opponentMoves = ZebraBitBoard.legalMoves(opponent, player);
        long playerFrontier = player & ZebraBitBoard.adjacent(empty);
        long opponentFrontier = opponent & ZebraBitBoard.adjacent(empty);
        long playerPotential = empty & ZebraBitBoard.adjacent(opponent);
        long opponentPotential = empty & ZebraBitBoard.adjacent(player);
        features[0] = Long.bitCount(playerMoves) - Long.bitCount(opponentMoves);
        features[1] = Long.bitCount(playerPotential) - Long.bitCount(opponentPotential);
        features[2] = Long.bitCount(opponentFrontier) - Long.bitCount(playerFrontier);
        features[3] = Long.bitCount(player & ZebraBitBoard.CORNERS) - Long.bitCount(opponent & ZebraBitBoard.CORNERS);
        features[4] = cornerDanger(opponent, occupied) - cornerDanger(player, occupied);
        features[5] = stableEdgeCount(player) - stableEdgeCount(opponent);
        features[6] = Long.bitCount(player) - Long.bitCount(opponent);
        features[7] = Long.bitCount(player & ZebraBitBoard.EDGES) - Long.bitCount(opponent & ZebraBitBoard.EDGES);
        features[8] = (Long.bitCount(empty) & 1) == 1 ? 1 : -1;
        features[9] = Long.bitCount(playerMoves) == 1 ? 1 : 0;
    }

    private static int cornerDanger(long board, long occupied) {
        int danger = 0;
        for (int i = 0; i < CORNER_BITS.length; i++) {
            if ((occupied & CORNER_BITS[i]) == 0L) {
                danger += Long.bitCount(board & EMPTY_CORNER_NEIGHBORS[i]);
            }
        }
        return danger;
    }

    private static int stableEdgeCount(long board) {
        int stable = 0;
        if ((board & 1L) != 0L) {
            stable += run(board, 0, 1) + run(board, 0, 8) - 1;
        }
        if ((board & (1L << 7)) != 0L) {
            stable += run(board, 7, -1) + run(board, 7, 8) - 1;
        }
        if ((board & (1L << 56)) != 0L) {
            stable += run(board, 56, 1) + run(board, 56, -8) - 1;
        }
        if ((board & (1L << 63)) != 0L) {
            stable += run(board, 63, -1) + run(board, 63, -8) - 1;
        }
        return stable;
    }

    private static int run(long board, int start, int delta) {
        int count = 0;
        int square = start;
        while (count < 8 && square >= 0 && square < 64 && (board & (1L << square)) != 0L) {
            count++;
            square += delta;
        }
        return count;
    }

    private static int[][] buildCornerPatterns() {
        int[] base = {0, 1, 2, 8, 9, 10};
        int[][] patterns = new int[8][base.length];
        for (int transform = 0; transform < 8; transform++) {
            for (int i = 0; i < base.length; i++) {
                patterns[transform][i] = ZebraBitBoard.transformSquare(base[i], transform);
            }
        }
        return patterns;
    }
}
