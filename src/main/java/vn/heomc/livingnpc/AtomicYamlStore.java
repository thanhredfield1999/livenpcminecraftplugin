package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

final class AtomicYamlStore {
    private AtomicYamlStore() {
    }

    static boolean save(YamlConfiguration yaml, File file, Logger logger, String description) {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        long startNanos = System.nanoTime();
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            SaveTelemetry.record(logger, description, startNanos, file.length());
            return true;
        } catch (IOException exception) {
            logger.severe("Could not save " + description + ": " + exception.getMessage());
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException exception) {
                logger.warning("Could not remove stale temporary file " + temporary.getName() + ": " + exception.getMessage());
            }
        }
    }
}
