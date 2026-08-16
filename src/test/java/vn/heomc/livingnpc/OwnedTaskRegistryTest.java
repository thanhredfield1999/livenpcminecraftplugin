package vn.heomc.livingnpc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

final class OwnedTaskRegistryTest {
    @Test
    void cancelAllHuyMoiTaskDungMotLan() {
        OwnedTaskRegistry registry = new OwnedTaskRegistry();
        BukkitTask first = mock(BukkitTask.class);
        BukkitTask second = mock(BukkitTask.class);
        registry.add(first);
        registry.add(second);

        registry.cancelAll();
        registry.cancelAll();

        verify(first, times(1)).cancel();
        verify(second, times(1)).cancel();
    }

    @Test
    void removeChuyenQuyenSoHuuRaKhoiRegistry() {
        OwnedTaskRegistry registry = new OwnedTaskRegistry();
        BukkitTask completed = mock(BukkitTask.class);
        registry.add(completed);

        registry.remove(completed);
        registry.cancelAll();

        verify(completed, times(0)).cancel();
    }
}
