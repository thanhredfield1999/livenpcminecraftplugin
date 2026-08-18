package vn.heomc.livingnpc;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded latency and size telemetry for synchronous YAML file writes.
 * Records elapsed time and file size after each successful save.
 * Logs WARNING when the write exceeds a configurable threshold, FINE otherwise.
 * Pure measurement — does not alter save behavior, threading, or crash consistency.
 */
final class SaveTelemetry {
    static final long SLOW_THRESHOLD_MICROS = 5_000L;

    private SaveTelemetry() {
    }

    static void record(Logger logger, String description, long startNanos, long fileBytes) {
        long elapsedMicros = (System.nanoTime() - startNanos) / 1_000L;
        String message = "YAML_SAVE description=" + description
                + " elapsedMicros=" + elapsedMicros
                + " fileBytes=" + fileBytes;
        if (elapsedMicros >= SLOW_THRESHOLD_MICROS) {
            logger.warning(message);
        } else {
            logger.log(Level.FINE, message);
        }
    }
}
