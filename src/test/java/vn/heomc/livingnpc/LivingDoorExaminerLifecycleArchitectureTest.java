package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LivingDoorExaminerLifecycleArchitectureTest {
    @Test
    void managedCloseMustRejectLateRuntimeCallback() throws Exception {
        String source = source();
        int start = source.indexOf("static void scheduleManagedClose(");
        int end = source.indexOf("private static boolean isSupported", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("!accepting"),
                "close task must not be created after runtime shutdown");
    }

    @Test
    void staleOpenCallbacksMustNotMutateBlocksAfterRuntimeStop() throws Exception {
        String source = source();
        int start = source.indexOf("static boolean openAfterAuthorization(");
        int end = source.indexOf("static void scheduleManagedClose", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("!accepting"),
                "late authorization callback must fail closed before block mutation");
    }

    private static String source() throws Exception {
        return Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/LivingDoorExaminer.java"),
                StandardCharsets.UTF_8);
    }
}
