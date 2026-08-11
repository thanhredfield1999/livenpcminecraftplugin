package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResidentProfileTest {
    @Test
    void supportsMultipleImmutableRoles() {
        EnumSet<ResidentRole> roles = EnumSet.of(ResidentRole.FARMER, ResidentRole.FISHER);
        ResidentProfile profile = new ResidentProfile("worker", "Worker", "unspecified", "Worker", roles, "");
        roles.clear();

        assertEquals(2, profile.roles().size());
        assertTrue(profile.hasRole(ResidentRole.FARMER));
        assertTrue(profile.hasRole(ResidentRole.FISHER));
    }

    @Test
    void parsesLegacyProfessionAliases() {
        assertEquals(ResidentRole.COOK, ResidentRole.parse("baker"));
        assertEquals(ResidentRole.SECURITY, ResidentRole.parse("sentry"));
    }

    @Test
    void updatesAndResetsOneRoleScheduleWithoutChangingOtherData() {
        ResidentProfile profile = new ResidentProfile(
                "worker", "Worker", "unspecified", "Worker",
                EnumSet.of(ResidentRole.FARMER, ResidentRole.FISHER), "");
        FarmerDefinition original = new FarmerDefinition(
                java.util.UUID.randomUUID(),
                new StoredLocation("world", 0, 64, 0, 0, 0),
                null,
                4,
                profile,
                ResidentRole.FISHER,
                Map.of(ResidentRole.FARMER, new RoleProgress(40L)),
                Map.of(ResidentRole.FARMER, new ResidentSchedule(1000, 12000)),
                BehaviorFlag.safeDefaults());

        FarmerDefinition changed = original.withSchedule(
                ResidentRole.FISHER, new ResidentSchedule(12000, 22000));
        FarmerDefinition reset = changed.withSchedule(ResidentRole.FISHER, null);

        assertFalse(original.schedules().containsKey(ResidentRole.FISHER));
        assertEquals(new ResidentSchedule(12000, 22000), changed.schedules().get(ResidentRole.FISHER));
        assertEquals(new ResidentSchedule(1000, 12000), changed.schedules().get(ResidentRole.FARMER));
        assertEquals(ResidentRole.FISHER, changed.activeRole());
        assertEquals(40L, changed.progress(ResidentRole.FARMER).experience());
        assertFalse(reset.schedules().containsKey(ResidentRole.FISHER));
    }
}
