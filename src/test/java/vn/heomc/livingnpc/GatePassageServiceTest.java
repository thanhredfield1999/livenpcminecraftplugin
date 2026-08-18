package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GatePassageServiceTest {
    @Test
    void shutdownClearsGlobalServiceReference() {
        GatePassageService service = new GatePassageService(new DoorPassageCoordinator());

        assertTrue(GatePassageService.isActive());

        service.shutdown();

        assertFalse(GatePassageService.isActive());
    }
}
