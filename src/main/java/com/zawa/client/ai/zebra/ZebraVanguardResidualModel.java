package com.zawa.client.ai.zebra;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZebraVanguardResidualModel {
    public static final String DEFAULT_FILE_NAME = "zebra_vanguard_residual.bin";
    public static final int MAGIC = 0x5A565244;
    public static final int VERSION = 1;

    private final float[][] weights;
    private final int maximumAdjustment;

    private ZebraVanguardResidualModel(float[][] weights, int maximumAdjustment) {
        this.weights = weights;
        this.maximumAdjustment = maximumAdjustment;
    }

    public static ZebraVanguardResidualModel loadDefault() {
        try (InputStream resource = ZebraVanguardResidualModel.class.getResourceAsStream("/" + DEFAULT_FILE_NAME)) {
            if (resource == null) {
                throw new IOException("missing resource " + DEFAULT_FILE_NAME);
            }
            return read(resource);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError("Cannot load ZebraVanguard residual: " + exception.getMessage());
        }
    }

    public static ZebraVanguardResidualModel load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return read(input);
        }
    }

    int adjustment(int discs, double[] features) {
        int stage = Math.max(0, Math.min(weights.length - 1, (discs - 13) >> 2));
        float[] stageWeights = weights[stage];
        double score = 0.0;
        for (int index = 0; index < stageWeights.length; index++) {
            score += stageWeights[index] * features[index];
        }
        return (int) Math.round(Math.max(-maximumAdjustment, Math.min(maximumAdjustment, score)));
    }

    public int stages() {
        return weights.length;
    }

    public int maximumAdjustment() {
        return maximumAdjustment;
    }

    public float weight(int stage, int feature) {
        return weights[stage][feature];
    }

    private static ZebraVanguardResidualModel read(InputStream raw) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(raw))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported residual model header");
            }
            int stages = input.readInt();
            int features = input.readInt();
            int maximumAdjustment = input.readInt();
            if (stages <= 0 || stages > 64 || features != ZebraStrategicFeatures.COUNT
                    || maximumAdjustment <= 0 || maximumAdjustment > 10_000) {
                throw new IOException("invalid residual model dimensions");
            }
            float[][] weights = new float[stages][features];
            for (int stage = 0; stage < stages; stage++) {
                for (int feature = 0; feature < features; feature++) {
                    float weight = input.readFloat();
                    if (!Float.isFinite(weight)) {
                        throw new IOException("non-finite residual weight");
                    }
                    weights[stage][feature] = weight;
                }
            }
            if (input.read() != -1) {
                throw new IOException("trailing residual model data");
            }
            return new ZebraVanguardResidualModel(weights, maximumAdjustment);
        } catch (EOFException exception) {
            throw new IOException("truncated residual model", exception);
        }
    }
}
