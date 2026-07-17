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

    EnumServerMessage whichMessage(String message){
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
        } else if (message.startsWith("CLOSE")){
            return EnumServerMessage.CLOSE;
        } else {
            return null;
        }
    }
}
