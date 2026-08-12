package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfessionRuntimeOwnershipTest {
    @Test
    void rancherOwnsOnlyRancherRole() {
        assertTrue(RancherRuntime.ownsRole(ResidentRole.RANCHER));
        assertFalse(RancherRuntime.ownsRole(ResidentRole.FISHER));
        assertFalse(RancherRuntime.ownsRole(ResidentRole.RESIDENT));
    }

    @Test
    void civilRuntimeDoesNotOwnFisherOrRancher() {
        assertTrue(CivilProfessionRuntime.ownsRole(ResidentRole.COOK));
        assertTrue(CivilProfessionRuntime.ownsRole(ResidentRole.SECURITY));
        assertFalse(CivilProfessionRuntime.ownsRole(ResidentRole.FISHER));
        assertFalse(CivilProfessionRuntime.ownsRole(ResidentRole.RANCHER));
    }

    @Test
    void switchingBetweenOwnedRolesChangesTheWorkAssignment() {
        FarmerDefinition cook = definition("village-a", ResidentRole.COOK);

        assertTrue(CivilProfessionRuntime.workAssignmentChanged(
                cook, cook.withActiveRole(ResidentRole.CRAFTER)));
    }

    @Test
    void movingToAnotherVillageChangesTheWorkAssignment() {
        FarmerDefinition cook = definition("village-a", ResidentRole.COOK);

        assertTrue(CivilProfessionRuntime.workAssignmentChanged(
                cook, cook.withVillage("village-b")));
        assertFalse(CivilProfessionRuntime.workAssignmentChanged(
                cook, cook.withBehavior(BehaviorFlag.REST, false)));
    }

    private FarmerDefinition definition(String villageId, ResidentRole activeRole) {
        UUID uuid = UUID.randomUUID();
        ResidentProfile profile = new ResidentProfile(
                "worker", "Worker", "unspecified", "Worker",
                Set.of(ResidentRole.COOK, ResidentRole.CRAFTER), "");
        return new FarmerDefinition(
                uuid, villageId, new StoredLocation("world", 0, 64, 0, 0, 0), null, 4,
                profile, activeRole, Map.of(), Map.of(), EnumSet.of(BehaviorFlag.MASTER));
    }
}
