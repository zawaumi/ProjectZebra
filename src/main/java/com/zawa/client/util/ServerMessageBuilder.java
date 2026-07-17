package com.zawa.client.util;
import com.zawa.client.network.EnumServerMessage;
import com.zawa.client.network.EnumServerProtocol;

public class ServerMessageBuilder {
    ServerMessageBuilder(){
    }
    String prefix(EnumServerMessage message){
        switch(message){
            case SAY:
                return "SAY ";
            case BOARD:
                return "BOARD ";
            case TURN:
                return "TURN ";
            case END:
                return "END ";
            case START:
                return "START ";
            case CLOSE:
                return "CLOSE";
            default:
                return null;
        }
    }
}
