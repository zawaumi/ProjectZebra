package com.zawa.client.network;

import com.zawa.client.registry.ClientProtocolRegistry;
import com.zawa.client.util.AbstractClientItems;

public class ClientProtocols extends AbstractClientItems<AbstractClientProtocol> {
    public static final ClientProtocolRegistry REGISTRY = new ClientProtocolRegistry();

    @Override
    public ClientProtocolRegistry getRegistry() {
        return REGISTRY;
    }

    @Override
    public void register() {
        REGISTRY.register(new ClientFtpProtocol());
    }
}