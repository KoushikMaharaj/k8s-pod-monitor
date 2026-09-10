package com.example.podmonitor;

import java.awt.TrayIcon;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages the polling timer and wake-from-sleep detection.
 */
public class TimerManager {

    private static Timer   mainTimer  = null;
    private static Thread  wakeThread = null;
    private static volatile boolean running = false;

    private static final long[] lastCheckAt = {System.currentTimeMillis()};

    public static void start(TrayIcon trayIcon, MonitorConfig cfg) {
        running = true;
        Logger.info("Timer started — interval={}min", cfg.intervalMs / 60_000);
        scheduleTimer(trayIcon, cfg);
        startWakeDetector(trayIcon, cfg);
    }

    public static void stop() {
        running = false;
        if (mainTimer != null) {
            mainTimer.cancel();
            mainTimer = null;
        }
        Logger.info("Timer stopped");
    }

    // ── Polling timer ─────────────────────────────────────────────────────────

    private static void scheduleTimer(TrayIcon trayIcon, MonitorConfig cfg) {
        if (mainTimer != null) mainTimer.cancel();
        mainTimer = new Timer("pod-poll-timer", true);
        mainTimer.schedule(new TimerTask() {
            public void run() {
                lastCheckAt[0] = System.currentTimeMillis();
                Logger.debug("Scheduled poll triggered");
                PodChecker.checkAndNotify(trayIcon, cfg);
            }
        }, cfg.intervalMs, cfg.intervalMs);
        Logger.debug("Poll timer scheduled — next check in {}min", cfg.intervalMs / 60_000);
    }

    // ── Wake-from-sleep detector ──────────────────────────────────────────────

    private static void startWakeDetector(TrayIcon trayIcon, MonitorConfig cfg) {
        wakeThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(30_000);
                    long now = System.currentTimeMillis();
                    long gap = now - lastCheckAt[0];

                    if (gap > 2 * 60_000) {
                        Logger.warn("System woke from sleep — gap={}s, running immediate check",
                                gap / 1000);
                        scheduleTimer(trayIcon, cfg);
                        PodChecker.checkAndNotify(trayIcon, cfg);
                    }
                    lastCheckAt[0] = now;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Logger.error("Wake detector error", e);
                }
            }
        }, "wake-detector");
        wakeThread.setDaemon(true);
        wakeThread.start();
        Logger.debug("Wake-from-sleep detector started");
    }
}
