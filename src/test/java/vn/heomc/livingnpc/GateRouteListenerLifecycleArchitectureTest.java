package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GateRouteListenerLifecycleArchitectureTest {
    @Test
    void stoppedRuntimeMustCancelManagedGateEvent() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/GateRouteListener.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("public void onNpcOpenGate");
        int end = source.indexOf("FarmerDefinition definition", start);
        String guard = source.substring(start, end);

        assertTrue(guard.contains("event.setCancelled(true)"),
                "stopped runtime must fail closed before Citizens opens gate");
    }
}
