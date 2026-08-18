package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GatePassageServiceArchitectureTest {
    @Test
    void failedGateOpenMustReleaseCoordinatorOwner() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/GatePassageService.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private boolean open(");
        int end = source.indexOf("private static Block block", start);
        String open = source.substring(start, end);

        int guard = open.indexOf("if (!(gate.getBlockData() instanceof");
        int nextBranch = open.indexOf("if (openable.isOpen())", guard);
        assertTrue(guard >= 0 && nextBranch > guard
                        && open.substring(guard, nextBranch)
                                .contains("coordinator.release(key, npc.getUniqueId()"),
                "failed gate open must release FIFO owner");
    }
}
