package com.zawa.client.ai;

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
    }
}
