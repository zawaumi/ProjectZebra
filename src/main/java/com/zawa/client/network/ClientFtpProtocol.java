package com.zawa.client.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

public class ClientFtpProtocol extends AbstractClientProtocol {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread senderThread;
    private Thread receiverThread;

    @Override
    public void connect(BlockingQueue<String> sendQueue, BlockingQueue<String> receiveQueue, String host, Integer port) {
        try {
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            senderThread = new Thread(() -> {
                try {
                    while(true) {
                        String msg = sendQueue.take();
                        writer.println(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            receiverThread = new Thread(() -> {
                try {
                    while(true){
                        String msg = reader.readLine();
                        if (msg != null && checkServerData(msg)) {
                            receiveQueue.put(msg);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            senderThread.start();
            receiverThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void disconnect() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
            if (senderThread != null) senderThread.interrupt();
            if (receiverThread != null) receiverThread.interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Boolean checkServerData(String message) {
        return whichServerMessage(message) != null;
    }

    @Override
    public Boolean checkClientData(String message) {
        return whichClientMessage(message) != null;
    }
}