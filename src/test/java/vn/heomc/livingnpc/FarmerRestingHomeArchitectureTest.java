package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FarmerRestingHomeArchitectureTest {
    @Test
    void restingPathDoesNotStartAmbientActivityAtHome() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/FarmerRuntime.java"),
                StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private void idleAtHome(");
        int methodEnd = source.indexOf("    private void takeLunchBreak(", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart);
        String method = source.substring(methodStart, methodEnd);
        assertTrue(method.contains("phase = FarmerPhase.RESTING"));
        assertTrue(!method.contains("startAmbient("),
                "RESTING không được khởi động ambient activity");
    }
}

