package com.example.podmonitor;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;

import java.io.File;

/**
 * Central logger wrapping SLF4J + Logback.
 * <p>
 * Two named loggers driven by logback.xml:
 * <p>
 * APP  (com.example.podmonitor.app) → ~/PodStatusNotifier/logs/app.log
 * Lifecycle events: startup, config, reconfigure, errors
 * <p>
 * POD  (com.example.podmonitor.pod) → ~/PodStatusNotifier/logs/pod-status.log
 * kubectl results: pod counts, alerts, raw output
 * <p>
 * Pattern:
 * app.log        [yyyy-MM-dd HH:mm:ss] [LEVEL] [thread] message
 * pod-status.log [yyyy-MM-dd HH:mm:ss] [LEVEL] message
 * <p>
 * Call Logger.init(appHome) ONCE at startup before any log call.
 */
public class Logger {

    // Set the property as early as possible — before any getLogger() call
    // so logback.xml resolves ${psn.log.dir} correctly on first load.
    static {
        String logDir = System.getProperty("user.home")
                + File.separator + "PodStatusNotifier"
                + File.separator + "logs";
        new File(logDir).mkdirs();
        System.setProperty("psn.log.dir", logDir);
    }

    private static final String APP_LOGGER = "com.example.podmonitor.app";
    private static final String POD_LOGGER = "com.example.podmonitor.pod";

    private static org.slf4j.Logger APP;
    private static org.slf4j.Logger POD;

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * @param appHome root app dir, e.g. ~/PodStatusNotifier
     *                Logs are written to appHome/logs/
     */
    public static void init(String appHome) {
        String logDir = appHome + File.separator + "logs";
        new File(logDir).mkdirs();

        // Expose to logback.xml as ${psn.log.dir}
        System.setProperty("psn.log.dir", logDir);

        // Force Logback to reload its config now that the property is set
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.reset();
        ch.qos.logback.classic.util.ContextInitializer initializer =
                new ch.qos.logback.classic.util.ContextInitializer(ctx);
        try {
            initializer.autoConfig();
        } catch (Exception e) {
            System.err.println("Logback config error: " + e.getMessage());
        }

        APP = LoggerFactory.getLogger(APP_LOGGER);
        POD = LoggerFactory.getLogger(POD_LOGGER);

        APP.info("=== Pod Status Notifier started ===");
        APP.info("Log directory: {}", logDir);
    }

    // ── Application logger → app.log ──────────────────────────────────────────

    public static void debug(String msg) {
        app().debug(msg);
    }

    public static void debug(String msg, Object... a) {
        app().debug(msg, a);
    }

    public static void info(String msg) {
        app().info(msg);
    }

    public static void info(String msg, Object... a) {
        app().info(msg, a);
    }

    public static void warn(String msg) {
        app().warn(msg);
    }

    public static void warn(String msg, Object... a) {
        app().warn(msg, a);
    }

    public static void error(String msg) {
        app().error(msg);
    }

    public static void error(String msg, Throwable t) {
        app().error(msg, t);
    }

    public static void error(String msg, Object... a) {
        app().error(msg, a);
    }

    /**
     * Legacy shim — Logger.log() maps to INFO on app logger.
     */
    public static void log(String msg) {
        app().info(msg);
    }

    // ── Pod status logger → pod-status.log ───────────────────────────────────

    public static void podDebug(String msg) {
        pod().debug(msg);
    }

    public static void podDebug(String msg, Object... a) {
        pod().debug(msg, a);
    }

    public static void podInfo(String msg) {
        pod().info(msg);
    }

    public static void podInfo(String msg, Object... a) {
        pod().info(msg, a);
    }

    public static void podWarn(String msg) {
        pod().warn(msg);
    }

    public static void podWarn(String msg, Object... a) {
        pod().warn(msg, a);
    }

    public static void podError(String msg, Throwable t) {
        pod().error(msg, t);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static org.slf4j.Logger app() {
        if (APP == null) APP = LoggerFactory.getLogger(APP_LOGGER);
        return APP;
    }

    private static org.slf4j.Logger pod() {
        if (POD == null) POD = LoggerFactory.getLogger(POD_LOGGER);
        return POD;
    }


}
