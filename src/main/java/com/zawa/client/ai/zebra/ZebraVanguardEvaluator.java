package com.zawa.client.ai.zebra;

public final class ZebraVanguardEvaluator implements ZebraPositionEvaluator {
    private final ZebraVanguardPatternModel patternModel;
    private final ZebraVanguardResidualModel residualModel;
    private final double[] features = new double[ZebraStrategicFeatures.COUNT];

    public ZebraVanguardEvaluator(ZebraVanguardPatternModel patternModel,
                                  ZebraVanguardResidualModel residualModel) {
        this.patternModel = patternModel;
        this.residualModel = residualModel;
    }

    public static ZebraVanguardEvaluator loadDefault() {
        return new ZebraVanguardEvaluator(ZebraVanguardPatternModel.loadDefault(),
                ZebraVanguardResidualModel.loadDefault());
    }

    @Override
    public int evaluate(long player, long opponent, long playerMoves) {
        int baseScore = patternModel.evaluate(player, opponent, playerMoves);
        ZebraStrategicFeatures.extract(player, opponent, playerMoves, baseScore, features);
        return baseScore + residualModel.adjustment(Long.bitCount(player | opponent), features);
    }
}
