package vn.heomc.livingnpc;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

final class RuntimeStopCoordinator {
    record Cleanup(String name, Runnable action) {
        Cleanup {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(action, "action");
        }
    }

    private final Logger logger;
    private final List<Cleanup> cleanups;
    private boolean running = true;

    RuntimeStopCoordinator(Logger logger, List<Cleanup> cleanups) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.cleanups = List.copyOf(cleanups);
    }

    void start() {
        running = true;
    }

    void stop() {
        if (!running) return;
        running = false;
        for (Cleanup cleanup : cleanups) {
            try {
                cleanup.action().run();
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE,
                        "LivingNPC runtime cleanup failed: " + cleanup.name() + "; remaining cleanup will continue",
                        exception);
            }
        }
    }
}
