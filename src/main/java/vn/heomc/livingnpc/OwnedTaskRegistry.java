package vn.heomc.livingnpc;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.scheduler.BukkitTask;

final class OwnedTaskRegistry {
    private final Set<BukkitTask> tasks = new HashSet<>();

    void add(BukkitTask task) {
        tasks.add(task);
    }

    void remove(BukkitTask task) {
        tasks.remove(task);
    }

    void cancelAll() {
        RuntimeException failure = null;
        for (BukkitTask task : Set.copyOf(tasks)) {
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        tasks.clear();
        if (failure != null) throw failure;
    }
}
