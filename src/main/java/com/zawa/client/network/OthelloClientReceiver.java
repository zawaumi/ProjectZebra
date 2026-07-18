package com.zawa.client.network;

import com.zawa.ProjectZebra;
import com.zawa.client.ai.AbstractClientAi;
import com.zawa.client.util.OthelloClientStatus;

public class OthelloClientReceiver {
    private final OthelloClientStatus status;
    private final AbstractClientAi ai;
    private final OthelloClientSender sender;

    public OthelloClientReceiver(OthelloClientStatus status, AbstractClientAi ai, OthelloClientSender sender) {
        this.status = status;
        this.ai = ai;
        this.sender = sender;
    }

    public void receive(String message) {
        if (message == null) return;

        if (message.startsWith("START")) {
            String[] parts = message.split(" ");
            if (parts.length > 1) {
                status.updateMyTurn(Integer.parseInt(parts[1]));
                ProjectZebra.gameScreen.refresh();
            }
        } else if (message.startsWith("BOARD")) {
            String[] parts = message.split(" ");
            if (parts.length >= 65) {
                Integer[][] board = new Integer[8][8];
                for (int i = 0; i < 64; i++) {
                    int x = i / 8;
                    int y = i % 8;
                    board[y][x] = Integer.parseInt(parts[i + 1]);
                }
                status.update(board);
                ProjectZebra.gameScreen.refresh();
            }
        } else if (message.startsWith("TURN")) {
            String[] parts = message.split(" ");
            if (parts.length > 1) {
                int turn = Integer.parseInt(parts[1]);
                status.updateTurn(turn);
                ProjectZebra.gameScreen.refresh();
                if (status.getMyTurn() == turn) {
                    Integer[] pos = ai.estimateNextPut(status.get(), status.getMyTurn());
                    if (pos != null) {
                        sender.sendPut(pos[1], pos[0]);
                    }
                }
            }
        } else if (message.startsWith("END")) {
            System.out.println(message);
        } else if (message.startsWith("SAY ")) {
            int startBracket = message.indexOf("<");
            int endBracket = message.indexOf(">");
            if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
                String senderName = message.substring(startBracket + 1, endBracket);
                if (!senderName.equals(status.getNickname())) {
                    status.updateVSNickname(senderName);
                    ProjectZebra.gameScreen.refresh();
                }
            }
            System.out.println(message.substring(4));
        } else if (message.startsWith("ERROR")) {
            System.out.println(message);
        }
    }
}