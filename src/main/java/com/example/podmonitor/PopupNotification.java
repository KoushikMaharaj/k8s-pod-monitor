package com.example.podmonitor;

import javax.swing.*;
import java.awt.*;

/**
 * Bottom-right toast popup — auto-dismisses after 10 seconds.
 */
public class PopupNotification {

    public static void show(String title, String message,
                            TrayIcon.MessageType type, TrayIcon trayIcon,
                            MonitorConfig cfg) {
        SwingUtilities.invokeLater(() -> build(title, message, type, cfg));
    }

    private static void build(String title, String message,
                               TrayIcon.MessageType type, MonitorConfig cfg) {

        boolean isAlert = type == TrayIcon.MessageType.WARNING
                || type == TrayIcon.MessageType.ERROR;

        JDialog popup = new JDialog();
        popup.setUndecorated(true);
        popup.setAlwaysOnTop(true);
        popup.setLayout(new BorderLayout());
        popup.getRootPane().setBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        popup.setBackground(Color.WHITE);

        popup.add(buildHeader(isAlert),                   BorderLayout.NORTH);
        popup.add(buildBody(message, isAlert, cfg, popup), BorderLayout.CENTER);

        popup.pack();
        popup.setSize(340, popup.getHeight());

        // Bottom-right of screen
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        popup.setLocation(
                screen.width  - popup.getWidth()  - 16,
                screen.height - popup.getHeight() - 50);
        popup.setVisible(true);

        // Auto-dismiss after 10 s
        new java.util.Timer().schedule(new java.util.TimerTask() {
            public void run() { SwingUtilities.invokeLater(popup::dispose); }
        }, 10_000);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private static JPanel buildHeader(boolean isAlert) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createMatteBorder(
                0, 0, 1, 0, new Color(230, 230, 230)));

        // Status dot
        JPanel dot = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isAlert ? new Color(220, 38, 38) : new Color(22, 163, 74));
                g2.fillOval(0, 3, 8, 8);
            }
        };
        dot.setPreferredSize(new Dimension(8, 14));
        dot.setOpaque(false);

        JLabel titleLabel = new JLabel(isAlert ? "Pod alert" : "Pods OK");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(new Color(30, 30, 30));

        JLabel timeLabel = new JLabel("just now");
        timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        timeLabel.setForeground(new Color(160, 160, 160));

        header.add(dot);
        header.add(titleLabel);
        header.add(Box.createHorizontalStrut(60));
        header.add(timeLabel);
        return header;
    }

    // ── Body ──────────────────────────────────────────────────────────────────

    private static JPanel buildBody(String message, boolean isAlert,
                                    MonitorConfig cfg, JDialog popup) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));

        // Message
        JLabel msgLabel = new JLabel(
                "<html><body style='width:240px'>"
                        + message.replace("\n", "<br>") + "</body></html>");
        msgLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        msgLabel.setForeground(new Color(90, 90, 90));
        msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Pills row
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pills.setOpaque(false);
        pills.setAlignmentX(Component.LEFT_ALIGNMENT);
        pills.add(pill("ns: " + cfg.namespace));
        pills.add(pill("ctx: " + cfg.contextName));

        // Buttons
        JButton dismissBtn = new JButton("Dismiss");
        styleBtn(dismissBtn, false, isAlert);
        dismissBtn.addActionListener(e -> popup.dispose());

        JButton showBtn = new JButton("Show status");
        styleBtn(showBtn, true, isAlert);
        showBtn.addActionListener(e -> {
            popup.dispose();
            PodStatusNotifier.showStatusWindow();
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.add(dismissBtn);
        btnRow.add(showBtn);

        body.add(msgLabel);
        body.add(Box.createVerticalStrut(10));
        body.add(pills);
        body.add(Box.createVerticalStrut(12));
        body.add(btnRow);
        return body;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JLabel pill(String text) {
        JLabel l = new JLabel("  " + text);
        l.setFont(new Font("Monospaced", Font.PLAIN, 11));
        l.setForeground(new Color(100, 100, 100));
        l.setOpaque(true);
        l.setBackground(new Color(245, 245, 245));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        return l;
    }

    private static void styleBtn(JButton b, boolean primary, boolean isAlert) {
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (primary) {
            Color bg = isAlert ? new Color(220, 38, 38) : new Color(37, 99, 235);
            Color br = isAlert ? new Color(185, 28, 28) : new Color(29, 78, 216);
            b.setFont(new Font("SansSerif", Font.BOLD, 12));
            b.setForeground(Color.WHITE);
            b.setBackground(bg);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(br, 1, true),
                    BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        } else {
            b.setFont(new Font("SansSerif", Font.PLAIN, 12));
            b.setForeground(new Color(90, 90, 90));
            b.setBackground(Color.WHITE);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                    BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        }
    }
}
