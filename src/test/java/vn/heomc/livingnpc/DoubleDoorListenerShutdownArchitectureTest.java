package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DoubleDoorListenerShutdownArchitectureTest {
    @Test
    void shutdownMustRunPassageNavigationCleanupBeforeReleasingLease() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/DoubleDoorListener.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("void shutdown() {");
        int end = source.indexOf("/** Mở lại listener", start);
        String shutdown = source.substring(start, end);

        assertTrue(shutdown.contains("terminatePassageCleanup"),
                "shutdown must cancel passage navigation and restore navigator state");
    }
}
