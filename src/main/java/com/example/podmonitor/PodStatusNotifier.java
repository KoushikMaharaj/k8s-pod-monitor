package com.example.podmonitor;

import org.apache.batik.transcoder.TranscoderInput;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * Entry point — shows the config dialog, then starts the system-tray monitor.
 */
public class PodStatusNotifier {

    static JFrame    statusFrame = null;
    static JTextArea textArea    = null;

    private static final String APP_HOME =
            System.getProperty("user.home") + File.separator + "PodStatusNotifier";

    public static void main(String[] args) throws Exception {

        // ── 1. Config dialog (blocks until user submits or cancels) ──
        MonitorConfig cfg = ConfigDialog.open();
        if (cfg == null) {
            System.exit(0);
        }

        // ── 2. Init logging ──────────────────────────────────────────
        Logger.init(APP_HOME);
        Logger.info("Application started");
        Logger.info("Config — context={} cluster={} namespace={} interval={}min",
                cfg.contextName, cfg.cluster, cfg.namespace, cfg.intervalMs / 60_000);

        // ── 3. Switch kube context ───────────────────────────────────
        Logger.debug("Switching kube context to: {}", cfg.contextName);
        ShellRunner.run("kubectl config use-context " + cfg.contextName);
        Logger.info("Kube context switched to: {}", cfg.contextName);

        // ── 4. System tray ───────────────────────────────────────────
        if (!SystemTray.isSupported()) {
            Logger.warn("System tray not supported on this platform");
            JOptionPane.showMessageDialog(null,
                    "System tray is not supported on this platform.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SystemTray tray     = SystemTray.getSystemTray();
        TrayIcon   trayIcon = buildTrayIcon(cfg.namespace);
        buildStatusWindow(cfg);

        trayIcon.addActionListener(e -> showStatusWindow());
        trayIcon.setPopupMenu(buildPopupMenu(tray, trayIcon, cfg, args));
        tray.add(trayIcon);
        Logger.info("System tray icon added — namespace={}", cfg.namespace);

        // ── 5. Immediate check ───────────────────────────────────────
        PodChecker.checkAndNotify(trayIcon, cfg);

        // ── 6. Scheduled timer + wake-from-sleep detection ───────────
        TimerManager.start(trayIcon, cfg);

        Thread.currentThread().join();
    }

    // ── Tray icon ─────────────────────────────────────────────────────────────

    static TrayIcon buildTrayIcon(String namespace) {
        try {
            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
            transcoder.addTranscodingHint(
                    org.apache.batik.transcoder.image.PNGTranscoder.KEY_WIDTH, 64f);
            transcoder.addTranscodingHint(
                    org.apache.batik.transcoder.image.PNGTranscoder.KEY_HEIGHT, 64f);

            try (InputStream is = PodStatusNotifier.class.getResourceAsStream("/icon.svg")) {
                transcoder.transcode(new TranscoderInput(is), null);
            }

            TrayIcon icon = new TrayIcon(transcoder.getImage(), "Pod Monitor — " + namespace);
            icon.setImageAutoSize(true);
            Logger.debug("Tray icon loaded from SVG");
            return icon;

        } catch (Exception e) {
            Logger.warn("SVG icon load failed, using fallback circle: {}", e.getMessage());
            BufferedImage bi = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(22, 163, 74));
            g.fillOval(0, 0, 32, 32);
            g.dispose();
            TrayIcon icon = new TrayIcon(bi, "Pod Monitor — " + namespace);
            icon.setImageAutoSize(true);
            return icon;
        }
    }

    // ── Popup menu ────────────────────────────────────────────────────────────

    static PopupMenu buildPopupMenu(SystemTray tray, TrayIcon trayIcon,
                                    MonitorConfig cfg, String[] args) {
        PopupMenu popup    = new PopupMenu();
        MenuItem checkNow  = new MenuItem("Check Pods Now");
        MenuItem showPanel = new MenuItem("Show Status");
        MenuItem configure = new MenuItem("⚙️ Reconfigure");
        MenuItem exit      = new MenuItem("Exit");

        checkNow.addActionListener(e -> {
            Logger.info("Manual check triggered by user");
            PodChecker.checkAndNotify(trayIcon, cfg);
        });

        showPanel.addActionListener(e -> showStatusWindow());

        configure.addActionListener(e -> {
            Logger.info("Reconfigure requested by user");
            TimerManager.stop();
            try {
                MonitorConfig newCfg = ConfigDialog.open();
                if (newCfg == null) {
                    Logger.info("Reconfigure cancelled — resuming with existing config");
                    TimerManager.start(trayIcon, cfg);
                    return;
                }
                Logger.info("Reconfigured — context={} namespace={} interval={}min",
                        newCfg.contextName, newCfg.namespace, newCfg.intervalMs / 60_000);
                Logger.debug("Switching context after reconfigure: {}", newCfg.contextName);
                ShellRunner.run("kubectl config use-context " + newCfg.contextName);
                PodChecker.checkAndNotify(trayIcon, newCfg);
                TimerManager.start(trayIcon, newCfg);
            } catch (Exception ex) {
                Logger.error("Reconfigure failed", ex);
            }
        });

        exit.addActionListener(e -> {
            Logger.info("Application exiting — user requested");
            System.exit(0);
        });

        popup.add(checkNow);
        popup.add(showPanel);
        popup.addSeparator();
        popup.add(configure);
        popup.addSeparator();
        popup.add(exit);
        return popup;
    }

    // ── Status window ─────────────────────────────────────────────────────────

    static void buildStatusWindow(MonitorConfig cfg) {
        statusFrame = new JFrame("Pod Status — " + cfg.namespace);
        statusFrame.setSize(960, 520);
        statusFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        textArea = new JTextArea();
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        textArea.setBackground(new Color(18, 18, 18));
        textArea.setForeground(new Color(80, 200, 120));
        textArea.setCaretColor(new Color(80, 200, 120));
        textArea.setEditable(false);

        JLabel topLabel = new JLabel(
                "  kubectl get po -n " + cfg.namespace + "  |  waiting for first check…");
        topLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        topLabel.setForeground(new Color(160, 160, 160));
        topLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        topLabel.setOpaque(true);
        topLabel.setBackground(new Color(28, 28, 28));

        JLabel bottomLabel = new JLabel(
                "  Context: " + cfg.contextName
                + "  |  Cluster: " + cfg.cluster
                + "  |  Interval: " + (cfg.intervalMs / 60_000) + " min");
        bottomLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        bottomLabel.setForeground(new Color(120, 120, 120));
        bottomLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        bottomLabel.setOpaque(true);
        bottomLabel.setBackground(new Color(28, 28, 28));

        statusFrame.setLayout(new BorderLayout());
        statusFrame.add(topLabel,                       BorderLayout.NORTH);
        statusFrame.add(new JScrollPane(textArea),      BorderLayout.CENTER);
        statusFrame.add(bottomLabel,                    BorderLayout.SOUTH);
        statusFrame.getContentPane().setBackground(new Color(18, 18, 18));
    }

    static void showStatusWindow() {
        SwingUtilities.invokeLater(() -> {
            statusFrame.setVisible(true);
            statusFrame.toFront();
        });
    }

    static void updateStatusWindow(String output, MonitorConfig cfg) {
        SwingUtilities.invokeLater(() -> {
            textArea.setText(output);
            Component north = ((BorderLayout) statusFrame.getContentPane().getLayout())
                    .getLayoutComponent(BorderLayout.NORTH);
            if (north instanceof JLabel) {
                ((JLabel) north).setText(
                        "  kubectl get po -n " + cfg.namespace
                        + "   |   " + new java.util.Date());
            }
        });
    }

    // ── Batik helper ──────────────────────────────────────────────────────────

    static class BufferedImageTranscoder
            extends org.apache.batik.transcoder.image.PNGTranscoder {

        private BufferedImage image;

        @Override
        public void writeImage(BufferedImage img,
                               org.apache.batik.transcoder.TranscoderOutput output) {
            this.image = img;
        }

        public BufferedImage getImage() { return image; }
    }
}
