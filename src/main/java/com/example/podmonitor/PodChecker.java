package com.example.podmonitor;

import java.awt.Toolkit;
import java.awt.TrayIcon;

/**
 * Runs kubectl, parses pod statuses, triggers popup notification.
 * All pod-related log lines go to pod-status.log via Logger.pod*().
 */
public class PodChecker {

    public static void checkAndNotify(TrayIcon trayIcon, MonitorConfig cfg) {
        new Thread(() -> {
            try {
                Logger.podDebug("Running: kubectl get po -n {}", cfg.namespace);
                String output = ShellRunner.run("kubectl get po -n " + cfg.namespace);

                boolean hasBadPods = output.lines().anyMatch(cfg::isBadLine);

                long total   = output.lines().filter(l -> !l.startsWith("NAME")).count();
                long running = output.lines().filter(l -> l.contains("Running")).count();
                long bad     = total - running;

                Logger.podInfo("Total: {} | Running: {} | Bad: {}", total, running, bad);

                if (hasBadPods) {
                    String badNames = getBadPodNames(output, cfg).replace("\n", ", ");
                    Logger.podWarn("ALERT — bad pods detected: {}", badNames);
                    Logger.podDebug("Full kubectl output:\n{}", output);

                    // Also log to app.log so it appears in the app audit trail
                    Logger.warn("Pod alert in namespace={} — bad pods: {}", cfg.namespace, badNames);
                } else {
                    Logger.podInfo("OK — all {} pods running fine", total);
                }

                String title   = hasBadPods
                        ? "Pod Alert — " + cfg.namespace
                        : "Pods OK — "   + cfg.namespace;
                String message = hasBadPods
                        ? bad + " pod(s) not Running!\n" + getBadPodNames(output, cfg)
                        : "All " + total + " pods Running fine.";

                TrayIcon.MessageType type = hasBadPods
                        ? TrayIcon.MessageType.WARNING
                        : TrayIcon.MessageType.INFO;

                PodStatusNotifier.updateStatusWindow(output, cfg);

                if (hasBadPods) Toolkit.getDefaultToolkit().beep();

                PopupNotification.show(title, message, type, trayIcon, cfg);

            } catch (Exception ex) {
                Logger.podError("kubectl check failed", ex);
                Logger.error("Pod check error in namespace={}", cfg.namespace, ex);
                PopupNotification.show("Pod Checker Error", ex.getMessage(),
                        TrayIcon.MessageType.ERROR, trayIcon, cfg);
            }
        }, "pod-checker").start();
    }

    static String getBadPodNames(String output, MonitorConfig cfg) {
        StringBuilder sb = new StringBuilder();
        output.lines()
                .filter(cfg::isBadLine)
                .map(l -> l.split("\\s+")[0])
                .forEach(name -> sb.append(name).append("\n"));
        return sb.toString();
    }
}
