package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

final class NpcTelemetryExporter {
    private final File target;
    private final Executor executor;
    private final Logger logger;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean writeQueued = new AtomicBoolean(false);
    private volatile long lastWriteMillis;
    private volatile long lastWriteBytes;
    private volatile String lastWriteStatus = "never";

    NpcTelemetryExporter(File dataFolder, String configuredPath, Executor executor, Logger logger) {
        this.target = NpcTelemetryExportPath.resolve(dataFolder, configuredPath);
        this.executor = executor;
        this.logger = logger;
    }

    boolean exportSnapshot(String json) {
        if (cancelled.get() || json == null) return false;
        if (!writeQueued.compareAndSet(false, true)) return false;
        try {
            executor.execute(() -> {
                try {
                    if (!cancelled.get()) writeSnapshot(json);
                } finally {
                    writeQueued.set(false);
                }
            });
            return true;
        } catch (RuntimeException exception) {
            writeQueued.set(false);
            lastWriteStatus = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            logger.log(Level.WARNING, "Could not queue NPC telemetry export: " + exception.getMessage(), exception);
            return false;
        }
    }

    synchronized void writeSnapshot(String json) {
        if (cancelled.get() || json == null) return;
        File parent = target.getParentFile();
        File temporary = null;
        long startNanos = System.nanoTime();
        try {
            Files.createDirectories(parent.toPath());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            temporary = File.createTempFile(target.getName() + ".", ".tmp", parent);
            Files.write(temporary.toPath(), bytes);
            try {
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            lastWriteMillis = System.currentTimeMillis();
            lastWriteBytes = bytes.length;
            lastWriteStatus = "ok";
            SaveTelemetry.record(logger, "telemetry export " + target.getName(), startNanos, bytes.length);
        } catch (IOException | RuntimeException exception) {
            lastWriteStatus = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            logger.log(Level.WARNING, "Could not export NPC telemetry to " + target + ": " + exception.getMessage(), exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary.toPath());
                } catch (IOException exception) {
                    logger.warning("Could not remove stale telemetry temporary file " + temporary.getName() + ": " + exception.getMessage());
                }
            }
        }
    }

    void cancel() {
        cancelled.set(true);
    }

    NpcTelemetryExportStatus status() {
        return new NpcTelemetryExportStatus(
                true, target.getPath(), lastWriteMillis, lastWriteBytes, lastWriteStatus, writeQueued.get(), cancelled.get());
    }
}
