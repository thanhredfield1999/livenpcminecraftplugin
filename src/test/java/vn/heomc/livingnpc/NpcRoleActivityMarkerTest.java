package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NpcRoleActivityMarkerTest {
    @Test
    void formatsStableMachineReadableCompletionMarker() {
        UUID uuid = UUID.fromString("3d1d6e6d-6f19-4214-b794-f3ba0c202a1d");

        assertEquals(
                "NPC_ROLE_ACTIVITY uuid=3d1d6e6d-6f19-4214-b794-f3ba0c202a1d role=fisher result=completed",
                NpcEconomy.roleActivityMarker(uuid, ResidentRole.FISHER));
    }
}
