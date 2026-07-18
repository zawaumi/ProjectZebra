package com.zawa.client.menu;

import com.zawa.client.util.AbstractClientItems;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class SelectMenu<T> {
    private final String description;
    private final com.zawa.client.util.AbstractClientItems<T> items;

    public SelectMenu(String description, AbstractClientItems<T> items) {
        this.description = description;
        this.items = items;
    }

    public T show() {
        List<T> itemList = items.getRegistry().getRegisteredItems();
        int[] selectedIndex = {0};
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        JFrame frame = new JFrame("Select Window");
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
                g.drawString(description, 20, 30);
                int drawY = 70;
                for (int i = 0; i < itemList.size(); i++) {
                    int distance = Math.abs(i - selectedIndex[0]);
                    if (distance <= 3) {
                        String prefix = (i == selectedIndex[0]) ? "● " : "○ ";
                        String suffix = (i == selectedIndex[0]) ? " <" : "";
                        String text = prefix + itemList.get(i).getClass().getSimpleName() + suffix;
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
                        if (selectedIndex[0] < itemList.size() - 1) {
                            selectedIndex[0]++;
                            panel.repaint();
                        }
                        break;
                    case KeyEvent.VK_ENTER:
                        result.set(itemList.get(selectedIndex[0]));
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
        return result.get();
    }
}