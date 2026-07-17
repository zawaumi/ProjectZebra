package com.zawa.client.registry;

import com.zawa.client.ai.ClientAbstractAi;

import java.util.ArrayList;
import java.util.List;

public class ClientAiRegistry {
    List<ClientAbstractAi> aiRegistered = new ArrayList<>();
    public void register(ClientAbstractAi ai) {
        aiRegistered.add(ai);
    }
    public void unregister(ClientAbstractAi ai) {
        aiRegistered.remove(ai);
    }
    public List<ClientAbstractAi> getRegisteredAis() {
        return aiRegistered;
    }

}
