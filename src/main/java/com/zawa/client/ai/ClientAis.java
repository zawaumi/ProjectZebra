package com.zawa.client.ai;

import com.zawa.client.registry.ClientAiRegistry;

public class ClientAis {
    public static final ClientAiRegistry REGISTRY = new ClientAiRegistry();
    public static void register() {
        REGISTRY.register(new ClientRandomAI());
        REGISTRY.register(new ClientRandomAI2());
        REGISTRY.register(new ClientMonteAI());
    }
}