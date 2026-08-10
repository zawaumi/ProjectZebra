package com.zawa.client.ai;

import com.zawa.client.ai.ab.ClientAlphaBetaAI;
import com.zawa.client.ai.ab.ClientAlphaBetaParityAI;
import com.zawa.client.ai.ab.ClientMobilityFrontierAlphaBetaAI;
import com.zawa.client.ai.learn.ClientDeepBitboardAI;
import com.zawa.client.ai.learn.ClientLearnedHeuristicAI;
import com.zawa.client.ai.learn.ClientMachineLearningAI;
import com.zawa.client.ai.random.ClientRandomAI;
import com.zawa.client.ai.random.ClientRandomAI2;
import com.zawa.client.ai.research.*;
import com.zawa.client.ai.zebra.ClientZebraTclPvsAI;
import com.zawa.client.ai.zebra.ClientZebraVanguardMpcAI;
import com.zawa.client.registry.ClientAiRegistry;
import com.zawa.client.util.AbstractClientItems;

public class ClientAis extends AbstractClientItems<AbstractClientAi> {
    public static final ClientAiRegistry REGISTRY = new ClientAiRegistry();

    @Override
    public ClientAiRegistry getRegistry() {
        return REGISTRY;
    }

    @Override
    public void register() {
        REGISTRY.register(new ClientRandomAI());
        REGISTRY.register(new ClientRandomAI2());
        REGISTRY.register(new ClientMonteAI());
//        REGISTRY.register(new ClientAlphaBetaAI());
//        REGISTRY.register(new ClientParityAI());
//        REGISTRY.register(new ClientAlphaBetaParityAI());
//        REGISTRY.register(new ClientMobilityFrontierAlphaBetaAI());
//        REGISTRY.register(new ClientMachineLearningAI());
//        REGISTRY.register(new ClientAdvancedBitboardAI());
//        REGISTRY.register(new ClientDeepBitboardAI());
//        REGISTRY.register(new ClientTacticalBitboardAI());
//        REGISTRY.register(new ClientAreaBitboardAI());
//        REGISTRY.register(new ClientAlternativeMCTSAI());
//        REGISTRY.register(new ClientLearnedHeuristicAI());
//        REGISTRY.register(new ClientZebraTclPvsAI());
//        REGISTRY.register(new ClientZebraVanguardMpcAI());
    }
}
