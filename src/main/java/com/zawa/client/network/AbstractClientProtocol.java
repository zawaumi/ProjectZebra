package com.zawa.client.network;

import java.util.concurrent.BlockingQueue;

public abstract class AbstractClientProtocol {
    static Thread SenderThread;
    static Thread ReceiverThread;
    public abstract void connect(BlockingQueue<String> message, String host, Integer port);
    public abstract void disconnect();
    public abstract Boolean checkClientData(String message);
    public abstract Boolean checkServerData(String message);
    EnumServerMessage whichServerMessage(String message){
        if (message.startsWith("SAY")){
            return EnumServerMessage.SAY;
        } else if (message.startsWith("BOARD")){
            return EnumServerMessage.BOARD;
        } else if (message.startsWith("TURN")){
            return EnumServerMessage.TURN;
        } else if (message.startsWith("END")){
            return EnumServerMessage.END;
        } else if (message.startsWith("START")){
            return EnumServerMessage.START;
        } else if (message.startsWith("CLOSE")) {
            return EnumServerMessage.CLOSE;
        } else if (message.startsWith("ERROR")) {
            return EnumServerMessage.ERROR;
        } else {
            return null;
        }
    }
    EnumServerMessage whichClientMessage(String message){
        if (message.startsWith("NICK")){
            return EnumServerMessage.NICK;
        } else if (message.startsWith("PUT")){
            return EnumServerMessage.PUT;
        } else if (message.startsWith("SAY")){
            return EnumServerMessage.SAY;
        } else {
            return null;
        }
    }
}
