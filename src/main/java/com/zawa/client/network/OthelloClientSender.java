package com.zawa.client.network;

import com.zawa.client.util.ClientMessageBuilder;
import java.util.concurrent.BlockingQueue;

public class OthelloClientSender {
    private final BlockingQueue<String> sendQueue;
    private final ClientMessageBuilder builder = new ClientMessageBuilder();

    public OthelloClientSender(BlockingQueue<String> sendQueue) {
        this.sendQueue = sendQueue;
    }

    public void sendNick(String nickname) {
        putToQueue(builder.build(EnumServerMessage.NICK, nickname));
    }

    public void sendPut(int x, int y) {
        putToQueue(builder.build(EnumServerMessage.PUT, x, y));
    }

    public void sendSay(String message) {
        putToQueue(builder.build(EnumServerMessage.SAY, message));
    }

    private void putToQueue(String message) {
        try {
            if (message != null) {
                sendQueue.put(message);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}