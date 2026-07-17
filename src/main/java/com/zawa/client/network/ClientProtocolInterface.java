package com.zawa.client.network;

public interface ClientProtocolInterface {
    public void connect(String host, Integer port);
    public void disconnect();
}
