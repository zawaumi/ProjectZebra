package com.zawa.client.screen;

import com.zawa.client.util.OthelloClientStatus;

import javax.swing.*;
import java.awt.*;

public class OthelloGameScreen extends JFrame {
    private final OthelloClientStatus status;
    private final JPanel panel;

    public OthelloGameScreen(OthelloClientStatus status) {
        this.status = status;
        setTitle("Othello Client");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                g.setColor(new Color(34, 139, 34));
                g.fillRect(40, 80, 400, 400);

                g.setColor(Color.BLACK);
                for (int i = 0; i <= 8; i++) {
                    g.drawLine(40, 80 + i * 50, 440, 80 + i * 50);
                    g.drawLine(40 + i * 50, 80, 40 + i * 50, 480);
                }

                Integer[][] board = status.get();
                int blackCount = 0;
                int whiteCount = 0;

                if (board != null) {
                    for (int r = 0; r < 8; r++) {
                        for (int c = 0; c < 8; c++) {
                            if (board[r][c] != null && board[r][c] != 0) {
                                if (board[r][c] == 1) {
                                    g.setColor(Color.BLACK);
                                    blackCount++;
                                } else {
                                    g.setColor(Color.WHITE);
                                    whiteCount++;
                                }
                                g.fillOval(45 + c * 50, 85 + r * 50, 40, 40);
                            }
                        }
                    }
                }

                g.setColor(Color.BLACK);
                g.setFont(new Font("Monospaced", Font.BOLD, 18));
                g.drawString("Black: " + blackCount + "   White: " + whiteCount, 40, 40);

                String currentTurn = "None";
                if (status.getTurn() != null) {
                    currentTurn = status.getTurn() == 1 ? "Black" : "White";
                }
                g.drawString("TURN: " + currentTurn, 40, 70);

                g.setFont(new Font("Monospaced", Font.PLAIN, 14));
                g.drawString("You: " + status.getNickname() + " (" + status.MyTurnToString() + ")", 40, 510);
                g.drawString("VS : " + status.getVSNickname(), 40, 530);
            }
        };
        add(panel);
    }

    public void refresh() {
        panel.repaint();
    }
}