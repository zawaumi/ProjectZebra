package com.zawa;

import com.zawa.client.ai.ClientAbstractAi;
import com.zawa.client.ai.ClientAis;
import com.zawa.client.network.AbstractClientProtocol;
import com.zawa.client.network.ClientProtocols;
import com.zawa.client.network.OthelloClientReceiver;
import com.zawa.client.network.OthelloClientSender;
import com.zawa.client.screen.OthelloGameScreen;
import com.zawa.client.util.OthelloClientStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class ProjectZebra {
    public static ClientAbstractAi aiSelected;
    public static AbstractClientProtocol protocolSelected;
    public static OthelloClientStatus status = new OthelloClientStatus();
    public static OthelloGameScreen gameScreen;
    public static BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    public static BlockingQueue<String> receiveQueue = new LinkedBlockingQueue<>();
    public static String host = "localhost";
    public static Integer port = 12345;

    public static void main(String[] args) {
        ClientAis.register();
        ClientProtocols.register();
        aiSelected = renderAiSelectMenu();
        protocolSelected = renderProtocolSelectMenu();

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

    public static ClientAbstractAi renderAiSelectMenu() {
        List<ClientAbstractAi> ais = ClientAis.REGISTRY.getRegisteredAis();
        int[] selectedIndex = {0};
        ClientAbstractAi[] result = new ClientAbstractAi[1];
        CountDownLatch latch = new CountDownLatch(1);
        JFrame frame = new JFrame("AI Select Window");
        frame.setSize(400, 350);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(new Font("Monospaced", Font.BOLD, 16));
                g.setColor(Color.WHITE);
                g.drawString("接続するAIを選んでください。", 20, 30);
                int drawY = 70;
                for (int i = 0; i < ais.size(); i++) {
                    int distance = Math.abs(i - selectedIndex[0]);
                    if (distance <= 3) {
                        String prefix = (i == selectedIndex[0]) ? "● " : "○ ";
                        String suffix = (i == selectedIndex[0]) ? " <" : "";
                        String text = prefix + ais.get(i).getClass().getSimpleName() + suffix;
                        if (distance == 3) {
                            g.setColor(Color.GRAY);
                        } else if (i == selectedIndex[0]) {
                            g.setColor(Color.CYAN);
                        } else {
                            g.setColor(Color.WHITE);
                        }
                        g.drawString(text, 20, drawY);
                        drawY += 25;
                    }
                }
                g.setColor(Color.LIGHT_GRAY);
                g.setFont(new Font("Monospaced", Font.PLAIN, 14));
                g.drawString("Press [↑] to StairUp", 20, drawY + 20);
                g.drawString("Press [↓] to StairDown", 20, drawY + 40);
                g.drawString("Press [Enter] to Select", 20, drawY + 60);
            }
        };
        frame.add(panel);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:
                        if (selectedIndex[0] > 0) {
                            selectedIndex[0]--;
                            panel.repaint();
                        }
                        break;
                    case KeyEvent.VK_DOWN:
                        if (selectedIndex[0] < ais.size() - 1) {
                            selectedIndex[0]++;
                            panel.repaint();
                        }
                        break;
                    case KeyEvent.VK_ENTER:
                        result[0] = ais.get(selectedIndex[0]);
                        frame.dispose();
                        latch.countDown();
                        break;
                }
            }
        });
        frame.setVisible(true);
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result[0];
    }

    public static AbstractClientProtocol renderProtocolSelectMenu() {
        List<AbstractClientProtocol> protocols = ClientProtocols.REGISTRY.getRegisteredProtocols();
        int[] selectedIndex = {0};
        AbstractClientProtocol[] result = new AbstractClientProtocol[1];
        CountDownLatch latch = new CountDownLatch(1);
        JFrame frame = new JFrame("Protocol Select Window");
        frame.setSize(400, 350);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(new Font("Monospaced", Font.BOLD, 16));
                g.setColor(Color.WHITE);
                g.drawString("接続するプロトコルを選んでください。", 20, 30);
                int drawY = 70;
                for (int i = 0; i < protocols.size(); i++) {
                    int distance = Math.abs(i - selectedIndex[0]);
                    if (distance <= 3) {
                        String prefix = (i == selectedIndex[0]) ? "● " : "○ ";
                        String suffix = (i == selectedIndex[0]) ? " <" : "";
                        String text = prefix + protocols.get(i).getClass().getSimpleName() + suffix;
                        if (distance == 3) {
                            g.setColor(Color.GRAY);
                        } else if (i == selectedIndex[0]) {
                            g.setColor(Color.CYAN);
                        } else {
                            g.setColor(Color.WHITE);
                        }
                        g.drawString(text, 20, drawY);
                        drawY += 25;
                    }
                }
                g.setColor(Color.LIGHT_GRAY);
                g.setFont(new Font("Monospaced", Font.PLAIN, 14));
                g.drawString("Press [↑] to StairUp", 20, drawY + 20);
                g.drawString("Press [↓] to StairDown", 20, drawY + 40);
                g.drawString("Press [Enter] to Select", 20, drawY + 60);
            }
        };
        frame.add(panel);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:
                        if (selectedIndex[0] > 0) {
                            selectedIndex[0]--;
                            panel.repaint();
                        }
                        break;
                    case KeyEvent.VK_DOWN:
                        if (selectedIndex[0] < protocols.size() - 1) {
                            selectedIndex[0]++;
                            panel.repaint();
                        }
                        break;
                    case KeyEvent.VK_ENTER:
                        result[0] = protocols.get(selectedIndex[0]);
                        frame.dispose();
                        latch.countDown();
                        break;
                }
            }
        });
        frame.setVisible(true);
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result[0];
    }
}