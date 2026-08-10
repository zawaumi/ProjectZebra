package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraPatternModel;

import java.nio.file.Files;

public final class OthelloTrainer {
    private OthelloTrainer() {
    }

    public static void main(String[] arguments) throws Exception {
        if (ZebraWthorTrainingConfig.requested(arguments)) {
            ZebraWthorBookTrainer.train(ZebraWthorTrainingConfig.parse(arguments));
            return;
        }
        if (ZebraVanguardTrainingConfig.requested(arguments)) {
            ZebraVanguardModelTrainer.train(ZebraVanguardTrainingConfig.parse(arguments));
            return;
        }
        ZebraTrainingConfig config = ZebraTrainingConfig.parse(arguments);
        ZebraPatternModel model = Files.isRegularFile(config.input())
                ? ZebraPatternModel.load(config.input())
                : ZebraPatternModel.loadDefault();
        if (config.initialGames() >= 0L) {
            model.setTrainedGames(config.initialGames());
        }
        new ZebraSelfPlayTrainer(model, config).run();
    }
}
