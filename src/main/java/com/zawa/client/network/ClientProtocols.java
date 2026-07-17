package com.zawa.client.network;


import com.zawa.client.registry.ClientProtocolRegistry;

public class ClientProtocols {
    public static final ClientProtocolRegistry REGISTRY = new ClientProtocolRegistry();
    public static void register() {
        REGISTRY.register(new ClientFtpProtocol());
    }
}
