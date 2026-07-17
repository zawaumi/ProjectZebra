package com.zawa.client.util;
import com.zawa.client.network.EnumServerMessage;
import com.zawa.client.network.EnumServerProtocol;

public class ClientMessageBuilder {
    ClientMessageBuilder(){
    }
    String prefix(EnumServerMessage message){
        switch(message){
            case NICK:
                return "NICK ";
            case PUT:
                return "PUT ";
            case SAY:
                return "SAY ";
            default:
                return null;
        }
    }

    String build(EnumServerMessage message, Object... args){
        switch(message){
            case NICK:
                return prefix(message) + args[0];
            case PUT:
                return prefix(message) + args[0] + " " + args[1];
            case SAY:
                return prefix(message) + args[0];
            default:
                return null;
        }
    }

    EnumServerMessage whichMessage(String message){
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
