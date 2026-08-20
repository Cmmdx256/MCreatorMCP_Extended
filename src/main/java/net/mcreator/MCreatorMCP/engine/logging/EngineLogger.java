package net.mcreator.MCreatorMCP.engine.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EngineLogger {

    private static final Logger LOG = LogManager.getLogger("ProjectIntelligence");

    public static void project(String message, Object... args) {
        LOG.info("[PROJECT] " + message, args);
    }

    public static void task(String message, Object... args) {
        LOG.info("[TASK] " + message, args);
    }

    public static void plan(String message, Object... args) {
        LOG.info("[PLAN] " + message, args);
    }

    public static void validation(String message, Object... args) {
        LOG.info("[VALIDATION] " + message, args);
    }

    public static void execution(String message, Object... args) {
        LOG.info("[EXECUTION] " + message, args);
    }

    public static void warn(String message, Object... args) {
        LOG.warn("[WARN] " + message, args);
    }

    public static void error(String message, Throwable t) {
        LOG.error("[ERROR] " + message, t);
    }
}
