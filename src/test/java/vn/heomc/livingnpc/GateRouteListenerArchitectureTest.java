package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GateRouteListenerArchitectureTest {
    @Test
    void listenerOwnsMonitorTasksAndHasRuntimeStopGuard() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/GateRouteListener.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("OwnedTaskRegistry monitorTasks"));
        assertTrue(source.contains("void shutdown()"));
        assertTrue(source.contains("monitorTasks.cancelAll()"));
        assertTrue(source.contains("if (!accepting)"));
        assertTrue(source.contains("event.setCancelled(true)"));
        assertTrue(source.contains("monitorTasks.remove(taskHolder[0])"));
    }
}
