package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    void copiesCharacterCollections() {
        List<String> goals = new java.util.ArrayList<>(List.of("Help the village"));
        Map<UUID, ResidentRelationship> relationships = new java.util.HashMap<>();
        UUID sibling = UUID.randomUUID();
        relationships.put(sibling, new ResidentRelationship("sibling", "Alex"));

        ResidentProfile profile = new ResidentProfile(
                "worker", "Worker", "unspecified", "Worker", Set.of(ResidentRole.FARMER), "",
                "Biography", List.of("Calm"), "Bow", goals, relationships);
        goals.clear();
        relationships.clear();

        assertEquals(List.of("Help the village"), profile.goals());
        assertEquals("Alex", profile.relationships().get(sibling).name());
        assertTrue(profile.hasCharacterDetails());
    }

    @Test
    void parsesLegacyProfessionAliases() {
        assertEquals(ResidentRole.COOK, ResidentRole.parse("baker"));
        assertEquals(ResidentRole.SECURITY, ResidentRole.parse("sentry"));
    }

    @Test
    void onlyFarmerUsesFarmerSetup() {
        assertTrue(ResidentRole.FARMER.usesFarmerSetup());
        assertFalse(ResidentRole.RESIDENT.usesFarmerSetup());
        assertFalse(ResidentRole.FISHER.usesFarmerSetup());
        assertFalse(ResidentRole.VISITOR.usesFarmerSetup());
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

    @Test
    void selectingResidentJobPreservesFarmerProgressAndSchedule() {
        ResidentProfile profile = new ResidentProfile(
                "worker", "Worker", "unspecified", "Worker", Set.of(ResidentRole.FARMER), "");
        UUID uuid = UUID.randomUUID();
        FarmerDefinition farmer = new FarmerDefinition(
                uuid,
                new StoredLocation("world", 0, 64, 0, 0, 0),
                null,
                4,
                profile,
                ResidentRole.FARMER,
                Map.of(ResidentRole.FARMER, new RoleProgress(40L)),
                Map.of(ResidentRole.FARMER, new ResidentSchedule(1000, 12000)),
                BehaviorFlag.safeDefaults());

        FarmerDefinition resident = farmer.withActiveRole(ResidentRole.RESIDENT);

        assertEquals(ResidentRole.RESIDENT, resident.activeRole());
        assertTrue(resident.profile().hasRole(ResidentRole.RESIDENT));
        assertEquals(40L, resident.progress(ResidentRole.FARMER).experience());
        assertEquals(new ResidentSchedule(1000, 12000), resident.schedules().get(ResidentRole.FARMER));
        assertEquals(0L, resident.progress(ResidentRole.RESIDENT).experience());
    }

    @Test
    void resettingProfessionKeepsOnlyHomeAndReturnsToOrdinaryResident() {
        UUID uuid = UUID.randomUUID();
        StoredLocation home = new StoredLocation("world", 10, 64, 20, 0, 0);
        ResidentProfile profile = new ResidentProfile(
                "worker", "Worker", "unspecified", "Farmer",
                EnumSet.of(ResidentRole.FARMER, ResidentRole.FISHER), "skin",
                "old biography", List.of("old trait"), "bow", List.of("old goal"), Map.of());
        FarmerDefinition farmer = new FarmerDefinition(
                uuid, "village-a", home,
                new StoredLocation("world", 30, 64, 40, 0, 0), 12,
                profile, ResidentRole.FISHER,
                Map.of(ResidentRole.FARMER, new RoleProgress(40L), ResidentRole.FISHER, new RoleProgress(9L)),
                Map.of(ResidentRole.FARMER, new ResidentSchedule(1000, 12000)),
                EnumSet.of(BehaviorFlag.MASTER, BehaviorFlag.HARVEST));

        FarmerDefinition reset = farmer.resetToOrdinaryResident("Worker");

        assertEquals(uuid, reset.npcUuid());
        assertEquals(home, reset.home());
        assertEquals(ResidentRole.RESIDENT, reset.activeRole());
        assertEquals(ResidentProfile.adopted("Worker"), reset.profile());
        assertEquals(null, reset.villageId());
        assertEquals(null, reset.plot());
        assertEquals(4, reset.plotRadius());
        assertEquals(0L, reset.progress(ResidentRole.RESIDENT).experience());
        assertEquals(Set.of(ResidentRole.RESIDENT), reset.progress().keySet());
        assertTrue(reset.schedules().isEmpty());
        assertEquals(BehaviorFlag.safeDefaults(), reset.behaviors());
    }

    @Test
    void assigningNewProfessionStartsFromOrdinaryResidentDefaults() {
        FarmerDefinition resident = new FarmerDefinition(
                UUID.randomUUID(), "Worker", new StoredLocation("world", 0, 64, 0, 0, 0), null, 4,
                ResidentProfile.adopted("Worker"), BehaviorFlag.safeDefaults());

        FarmerDefinition fisher = resident.resetToOrdinaryResident("Worker")
                .withActiveRole(ResidentRole.FISHER);

        assertEquals(ResidentRole.FISHER, fisher.activeRole());
        assertEquals(null, fisher.villageId());
        assertEquals(null, fisher.plot());
        assertEquals(Set.of(ResidentRole.RESIDENT, ResidentRole.FISHER), fisher.profile().roles());
        assertEquals(0L, fisher.progress(ResidentRole.FISHER).experience());
        assertTrue(fisher.schedules().isEmpty());
        assertEquals(BehaviorFlag.safeDefaults(), fisher.behaviors());
    }

    @Test
    void customCitizensProfileStartsAsResident() {
        ResidentProfile profile = ResidentProfile.adopted("Worker");

        assertEquals(Set.of(ResidentRole.RESIDENT), profile.roles());
        assertEquals(ResidentRole.RESIDENT, profile.primaryRole());
    }
}
