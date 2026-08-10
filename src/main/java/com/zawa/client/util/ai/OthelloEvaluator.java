package com.zawa.client.util.ai;

import com.zawa.client.util.table.BitBoardUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class OthelloEvaluator {
    private static final int FEATURE_COUNT = 7;
    private static double[] learnedWeights = {
        10.0, -5.0, -2.0, 2.0, 1.0, 5.0, -3.0
    };

    static {
        loadWeights();
    }

    private static void loadWeights() {
        File weightsFile = new File("learned_weights.txt");
        if (weightsFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(weightsFile))) {
                for (int i = 0; i < FEATURE_COUNT; i++) {
                    String line = reader.readLine();
                    if (line != null) {
                        learnedWeights[i] = Double.parseDouble(line.split(":")[1].trim());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static double evaluate(long myBoard, long opponentBoard) {
        double[] features = extractFeatures(myBoard, opponentBoard);
        double scoreSum = 0.0;
        for (int i = 0; i < FEATURE_COUNT; i++) {
            scoreSum += features[i] * learnedWeights[i];
        }
        return scoreSum;
    }

    private static double[] extractFeatures(long myBoard, long opponentBoard) {
        double[] features = new double[FEATURE_COUNT];
        features[0] = Long.bitCount(myBoard & BitBoardUtils.MASK_CORNER) - Long.bitCount(opponentBoard & BitBoardUtils.MASK_CORNER);
        features[1] = Long.bitCount(myBoard & BitBoardUtils.MASK_X) - Long.bitCount(opponentBoard & BitBoardUtils.MASK_X);
        features[2] = Long.bitCount(myBoard & BitBoardUtils.MASK_C) - Long.bitCount(opponentBoard & BitBoardUtils.MASK_C);
        features[3] = Long.bitCount(myBoard & BitBoardUtils.MASK_EDGE) - Long.bitCount(opponentBoard & BitBoardUtils.MASK_EDGE);
        features[4] = Long.bitCount(myBoard & BitBoardUtils.MASK_INNER) - Long.bitCount(opponentBoard & BitBoardUtils.MASK_INNER);
        features[5] = Long.bitCount(BitBoardUtils.getLegalMoves(myBoard, opponentBoard)) - Long.bitCount(BitBoardUtils.getLegalMoves(opponentBoard, myBoard));
        long emptyBoard = ~(myBoard | opponentBoard);
        features[6] = Long.bitCount(BitBoardUtils.getFrontier(myBoard, emptyBoard)) - Long.bitCount(BitBoardUtils.getFrontier(opponentBoard, emptyBoard));
        return features;
    }
}