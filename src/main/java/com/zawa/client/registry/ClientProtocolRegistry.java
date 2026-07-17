package com.zawa.client.registry;

import com.zawa.client.network.AbstractClientProtocol;

import java.util.ArrayList;
import java.util.List;

public class ClientProtocolRegistry {
    List<AbstractClientProtocol> protocolRegistered = new ArrayList<>();
    public void register(AbstractClientProtocol protocol) {
        protocolRegistered.add(protocol);
    }
    public void unregister(AbstractClientProtocol protocol) {
        protocolRegistered.remove(protocol);
    }
    public List<AbstractClientProtocol> getRegisteredProtocols() {
        return protocolRegistered;
    }
}
