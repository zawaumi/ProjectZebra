package com.zawa.client.ai.learn;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ZebraTeacherPatternModel {
    private static final int MAGIC = 0x50455631;
    private static final int VERSION = 1;

    private final int scale;
    private final int stages;
    private final int features;
    private final int stageTableSize;
    private final Family[] families;
    private final short[] weights;

    private ZebraTeacherPatternModel(int scale, int stages, int features, int stageTableSize,
                                    Family[] families, short[] weights) {
        this.scale = scale;
        this.stages = stages;
        this.features = features;
        this.stageTableSize = stageTableSize;
        this.families = families;
        this.weights = weights;
    }

    static ZebraTeacherPatternModel load(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported teacher model header");
            }
            int scale = input.readInt();
            int stages = input.readInt();
            int features = input.readInt();
            int familyCount = input.readInt();
            int stageTableSize = input.readInt();
            int weightCount = input.readInt();
            if (scale <= 0 || stages <= 0 || features <= 0 || familyCount <= 0 || stageTableSize <= 0
                    || (long) stages * stageTableSize != weightCount) {
                throw new IOException("invalid teacher model dimensions");
            }
            Family[] families = new Family[familyCount];
            int nextOffset = 0;
            int featureCount = 0;
            for (int familyIndex = 0; familyIndex < familyCount; familyIndex++) {
                String name = readName(input);
                int offset = input.readInt();
                int tableSize = input.readInt();
                int count = input.readInt();
                int squareCount = input.readInt();
                if (offset != nextOffset || tableSize <= 0 || count <= 0 || squareCount < 0 || squareCount > 20) {
                    throw new IOException("invalid teacher family " + name);
                }
                int[][] placements = new int[count][squareCount];
                for (int placement = 0; placement < count; placement++) {
                    for (int square = 0; square < squareCount; square++) {
                        placements[placement][square] = input.readUnsignedByte();
                        if (placements[placement][square] >= 64) {
                            throw new IOException("invalid square in teacher family " + name);
                        }
                    }
                }
                families[familyIndex] = new Family(name, offset, tableSize, placements);
                nextOffset += tableSize;
                featureCount += count;
            }
            if (nextOffset != stageTableSize || featureCount != features) {
                throw new IOException("incomplete teacher layout");
            }
            short[] weights = new short[weightCount];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = input.readShort();
            }
            if (input.read() != -1) {
                throw new IOException("trailing teacher model data");
            }
            return new ZebraTeacherPatternModel(scale, stages, features, stageTableSize, families, weights);
        } catch (EOFException exception) {
            throw new IOException("truncated teacher model", exception);
        }
    }

    int score(long player, long opponent) {
        int discs = Long.bitCount(player | opponent);
        int stage = Math.max(0, Math.min(stages - 1, (discs - 13) >> 2));
        int stageOffset = stage * stageTableSize;
        int score = 0;
        for (Family family : families) {
            if (family.parity()) {
                score += weights[stageOffset + family.offset() + ((64 - discs) & 1)];
                continue;
            }
            for (int[] squares : family.placements()) {
                int pattern = 0;
                int power = 1;
                for (int square : squares) {
                    long bit = 1L << square;
                    int piece = (player & bit) != 0L ? 1 : (opponent & bit) != 0L ? 2 : 0;
                    pattern += piece * power;
                    power *= 3;
                }
                score += weights[stageOffset + family.offset() + pattern];
            }
        }
        return score;
    }

    int scale() {
        return scale;
    }

    int stages() {
        return stages;
    }

    int features() {
        return features;
    }

    int stageTableSize() {
        return stageTableSize;
    }

    Family[] families() {
        return families;
    }

    short[] weights() {
        return weights;
    }

    private static String readName(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length == 0 || length > 64) {
            throw new IOException("invalid teacher family name length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    record Family(String name, int offset, int tableSize, int[][] placements) {
        boolean parity() {
            return "parity".equals(name);
        }
    }
}
