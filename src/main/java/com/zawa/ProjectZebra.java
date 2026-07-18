package com.zawa;

import com.zawa.client.ai.AbstractClientAi;
import com.zawa.client.ai.ClientAis;
import com.zawa.client.menu.SelectMenu;
import com.zawa.client.network.AbstractClientProtocol;
import com.zawa.client.network.ClientProtocols;
import com.zawa.client.network.OthelloClientReceiver;
import com.zawa.client.network.OthelloClientSender;
import com.zawa.client.screen.OthelloGameScreen;
import com.zawa.client.util.OthelloClientStatus;

import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProjectZebra {
    public static AbstractClientAi aiSelected;
    public static AbstractClientProtocol protocolSelected;
    public static OthelloClientStatus status = new OthelloClientStatus();
    public static OthelloGameScreen gameScreen;
    public static BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    public static BlockingQueue<String> receiveQueue = new LinkedBlockingQueue<>();
    public static String host = "localhost";
    public static Integer port = 12345;

    public static void main(String[] args) {
        ClientAis clientAis = new ClientAis();
        ClientProtocols clientProtocols = new ClientProtocols();
        SelectMenu<AbstractClientAi> AiSelectMenu = new SelectMenu<>("接続するAIを選んでください。", clientAis);
        SelectMenu<AbstractClientProtocol> ProtocolSelectMenu = new SelectMenu<>("接続するプロトコルを選んでください。", clientProtocols);

        clientAis.register();
        clientProtocols.register();

        aiSelected = AiSelectMenu.show();
        protocolSelected = ProtocolSelectMenu.show();

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your nickname: ");
        String nickname = scanner.nextLine();
        if (nickname != null && !nickname.trim().isEmpty()) {
            status.updateNickname(nickname.trim());
        }

        gameScreen = new OthelloGameScreen(status);
        gameScreen.setVisible(true);

        MainThread.start();
        ProtocolThread.start();
    }

    static Thread MainThread = new Thread(() -> {
        OthelloClientSender sender = new OthelloClientSender(sendQueue);
        OthelloClientReceiver receiver = new OthelloClientReceiver(status, aiSelected, sender);

        sender.sendNick(status.getNickname());
        sender.sendSay("GLHF!");
        while (true) {
            try {
                String message = receiveQueue.take();
                receiver.receive(message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    });

    static Thread ProtocolThread = new Thread(() -> {
        protocolSelected.connect(sendQueue, receiveQueue, host, port);
    });
}