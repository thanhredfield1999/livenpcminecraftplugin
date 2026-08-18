package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class NpcTelemetryTest {
    @Test
    void boundedBufferKeepsNewestEventsOnly() {
        NpcTelemetryBuffer buffer = new NpcTelemetryBuffer(2);
        buffer.record(event("first", 1L));
        buffer.record(event("second", 2L));
        buffer.record(event("third", 3L));

        NpcTelemetrySnapshot snapshot = buffer.snapshot();

        assertEquals(2, snapshot.events().size());
        assertEquals("second", snapshot.events().get(0).type());
        assertEquals("third", snapshot.events().get(1).type());
        assertEquals(2, snapshot.capacity());
        assertEquals(3, snapshot.totalRecorded());
    }

    @Test
    void jsonSnapshotExposesStableBotCheckerSchema() {
        NpcTelemetryBuffer buffer = new NpcTelemetryBuffer(4);
        buffer.record(event("ACTION", 44L));

        String json = NpcTelemetryJson.toJson(buffer.snapshot());

        assertTrue(json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains("\"capacity\":4"));
        assertTrue(json.contains("\"totalRecorded\":1"));
        assertTrue(json.contains("\"npcId\":\""));
        assertTrue(json.contains("\"npcBlock\":{"));
        assertTrue(json.contains("\"npcPrecise\":{"));
        assertTrue(json.contains("\"targetBlock\":{"));
        assertTrue(json.contains("\"targetPrecise\":{"));
        assertTrue(json.contains("\"state\":\"GOING_TO_PLOT\""));
        assertTrue(json.contains("\"navigation\":{"));
        assertTrue(json.contains("\"path\":\"present\""));
        assertTrue(json.contains("\"obstacle\":{"));
        assertTrue(json.contains("\"semanticPoint\":{"));
        assertTrue(json.contains("\"timestampTick\":44"));
        assertFalse(json.contains("\"account\":"));
    }

    @Test
    void jsonEventSerializesNpcAccountOnlyWhenPresent() {
        NpcTelemetryAccount account = new NpcTelemetryAccount(125L, "<minor>", 5,
                List.of(new NpcTelemetryInventoryItem("wheat", 3), new NpcTelemetryInventoryItem("<carrot>", 2)));
        NpcTelemetryEvent event = new NpcTelemetryEvent(1, "ACTION", UUID.randomUUID(), "Steve", "farmer", "world",
                null, null, "WORKING", "WORKING", null, null, null, null, List.of(), 1L, 2L, null, account);

        String json = NpcTelemetryJson.toJson(new NpcTelemetrySnapshot(1, 1, 1L, List.of(event)));

        assertTrue(json.contains("\"account\":{"));
        assertTrue(json.contains("\"balanceMinor\":125"));
        assertTrue(json.contains("\"currencyUnit\":\"<minor>\""));
        assertTrue(json.contains("\"inventoryTotal\":5"));
        assertTrue(json.contains("\"item\":\"<carrot>\""));
    }

    @Test
    void accountSnapshotBoundsInventoryAndSaturatesTotal() {
        NpcAccount source = new NpcAccount(UUID.randomUUID());
        for (int index = 0; index < 40; index++) source.setQuantity("item-" + index, 1);
        source.setQuantity("large", Integer.MAX_VALUE);
        source.setQuantity("overflow", 1);

        NpcTelemetryAccount snapshot = NpcTelemetryAccount.from(source);

        assertEquals(32, snapshot.inventory().size());
        assertEquals(Integer.MAX_VALUE, snapshot.inventoryTotal());
        assertEquals("item-0", snapshot.inventory().getFirst().item());
    }

    @Test
    void oldSnapshotConstructorKeepsOptionalEconomyVisitorsAndGatesAbsent() {
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 4, 0L, List.of());

        assertEquals(null, snapshot.economy());
        assertEquals(null, snapshot.visitors());
        assertTrue(snapshot.gates().isEmpty());
        assertFalse(NpcTelemetryJson.toJson(snapshot).contains("\"economy\""));
        assertFalse(NpcTelemetryJson.toJson(snapshot).contains("\"visitors\""));
        assertTrue(NpcTelemetryJson.toJson(snapshot).contains("\"gates\":[]"));
    }

    @Test
    void configuredGateStatesSerializeAsBoundedJson() {
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 4, 1L, List.of(), null, null, List.of(
                new NpcTelemetryGate("village-0-world-10-64-20", "world", 10, 64, 20,
                        "OAK_FENCE_GATE", true, "OPEN", "SHARED", 99L),
                new NpcTelemetryGate("village-1-world-11-64-20", "world", 11, 64, 20,
                        null, null, "UNKNOWN_UNAVAILABLE", "FARMER", 99L)));

        String json = NpcTelemetryJson.toJson(snapshot);

        assertTrue(json.contains("\"gates\":[{"));
        assertTrue(json.contains("\"material\":\"OAK_FENCE_GATE\""));
        assertTrue(json.contains("\"open\":true"));
        assertTrue(json.contains("\"status\":\"OPEN\""));
        assertTrue(json.contains("\"open\":null"));
        assertTrue(json.contains("\"status\":\"UNKNOWN_UNAVAILABLE\""));
    }

    @Test
    void optionalEconomyAndVisitorsSerializeAsBoundedJson() {
        NpcTelemetryVillageEconomy village = new NpcTelemetryVillageEconomy(
                "village-a", 42L, "minor",
                java.util.stream.IntStream.range(0, 40)
                        .mapToObj(index -> new NpcTelemetryInventoryItem("item-" + index, index + 1)).toList(),
                List.of(new NpcTelemetryRoleProduction("farmer", 3)),
                java.util.stream.IntStream.range(0, 40)
                        .mapToObj(index -> new NpcTelemetryActivity("farmer", "HARVEST", "wheat", index)).toList());
        NpcTelemetryVisitors visitors = new NpcTelemetryVisitors(true, 3, 17,
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(index -> new NpcTelemetryVisitor(UUID.randomUUID(), "Visitor-" + index, "village-a",
                                "visitor", "SHOPPING", 100L, List.of(), null, null))
                        .toList());
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 4, 0L, List.of(),
                new NpcTelemetryEconomySnapshot(List.of(village)), visitors);

        String json = NpcTelemetryJson.toJson(snapshot);

        assertEquals(32, village.inventory().size());
        assertEquals(32, village.activities().size());
        assertEquals(16, visitors.active().size());
        assertTrue(json.contains("\"economy\":{"));
        assertTrue(json.contains("\"visitors\":{"));
        assertTrue(json.contains("\"currencyUnit\":\"minor\""));
        assertTrue(json.contains("\"purchaseStatus\":null"));
    }

    @Test
    void blockClassifierIdentifiesDoorsGatesFencesAndObstacles() {
        NpcTelemetryBlockProbe gate = NpcTelemetryBlockProbe.classify(
                "front", "world", 1, 64, 2, Material.OAK_FENCE_GATE, false, true, true);
        NpcTelemetryBlockProbe door = NpcTelemetryBlockProbe.classify(
                "front", "world", 1, 64, 3, Material.OAK_DOOR, false, true, true);
        NpcTelemetryBlockProbe fence = NpcTelemetryBlockProbe.classify(
                "front", "world", 1, 64, 4, Material.OAK_FENCE, true, false, true);
        NpcTelemetryBlockProbe stone = NpcTelemetryBlockProbe.classify(
                "front", "world", 1, 64, 5, Material.STONE, true, false, true);

        assertTrue(gate.fenceGate());
        assertFalse(gate.fence());
        assertTrue(door.door());
        assertTrue(fence.fence());
        assertTrue(stone.obstacle());
        assertFalse(gate.obstacle());
    }

    @Test
    void collectorCreatesActionEventWithSemanticTargetAndBoundedProbes() {
        UUID uuid = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        when(world.isChunkLoaded(3, -1)).thenReturn(true);
        Block feet = block(world, 59, -60, -1, Material.AIR, true);
        Block head = block(world, 59, -59, -1, Material.AIR, true);
        Block support = block(world, 59, -61, -1, Material.DIRT_PATH, false);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(support);
        Block frontGate = block(world, 60, -60, -1, Material.OAK_FENCE_GATE, true);
        Block frontAir = block(world, 61, -60, -1, Material.AIR, true);
        Block frontStone = block(world, 62, -60, -1, Material.STONE, false);
        when(feet.getRelative(1, 0, 0)).thenReturn(frontGate);
        when(feet.getRelative(2, 0, 0)).thenReturn(frontAir);
        when(feet.getRelative(3, 0, 0)).thenReturn(frontStone);
        when(feet.getRelative(-1, 0, 0)).thenReturn(frontGate);
        when(feet.getRelative(-2, 0, 0)).thenReturn(frontAir);
        when(feet.getRelative(-3, 0, 0)).thenReturn(frontStone);
        FarmerDefinition definition = new FarmerDefinition(
                uuid,
                "stillcliff",
                new StoredLocation("StillCliff", 1.5, 64.0, 1.5, 0.0f, 0.0f),
                new StoredLocation("StillCliff", 60.5, -60.0, -0.5, 0.0f, 0.0f),
                4,
                new ResidentProfile("steve", "Steve", "male", "Nông dân", java.util.Set.of(ResidentRole.FARMER), ""),
                BehaviorFlag.safeDefaults());
        VillageDefinition village = new VillageDefinition(
                "stillcliff", "StillCliff", new StoredLocation("StillCliff", 0.0, 64.0, 0.0, 0.0f, 0.0f),
                null, null, null);
        Location current = new Location(world, 59.25, -60.0, -0.9, 90.0f, 0.0f);
        Location target = new Location(world, 60.5, -60.0, -0.5);
        NpcTelemetryBuffer buffer = new NpcTelemetryBuffer(8);
        NpcTelemetryCollector collector = new NpcTelemetryCollector(buffer);

        collector.recordAction(definition, village, "Steve", FarmerPhase.GOING_TO_PLOT,
                current, target, true, "AStarNavigationStrategy", "present", 99L);

        NpcTelemetryEvent event = buffer.snapshot().events().getFirst();
        assertEquals("ACTION", event.type());
        assertEquals(uuid, event.npcId());
        assertEquals("Steve", event.name());
        assertEquals("farmer", event.role());
        assertEquals("StillCliff", event.world());
        assertEquals("GOING_TO_PLOT", event.state());
        assertEquals("GOING_TO_PLOT", event.phase());
        assertEquals("present", event.path());
        assertEquals("PLOT", event.semanticPoint().type());
        assertEquals("plot", event.semanticPoint().name());
        assertEquals(99L, event.timestampTick());
        assertFalse(event.blockProbes().isEmpty());
    }

    @Test
    void collectorUsesKnownActionIdentityForNavigationEnd() {
        UUID uuid = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Block block = block(world, 1, 64, 1, Material.AIR, true);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        when(block.getRelative(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(block);
        FarmerDefinition definition = new FarmerDefinition(
                uuid, "village", null, null, 4,
                new ResidentProfile("steve", "Steve", "male", "Nông dân", java.util.Set.of(ResidentRole.FARMER), ""),
                BehaviorFlag.safeDefaults());
        Location current = new Location(world, 1.0, 64.0, 1.0);
        NpcTelemetryCollector collector = new NpcTelemetryCollector(new NpcTelemetryBuffer(8));

        collector.recordAction(definition, null, "Steve", FarmerPhase.WORKING,
                current, null, false, "none", "none", 1L);
        collector.recordNavigationEnd(uuid, "Other", "unknown", "STOP", "STUCK", current, null, null,
                1.0, 1.0, null, null, 1L);

        NpcTelemetryEvent navigationEnd = collector.snapshot().events().getLast();
        assertEquals("NAVIGATION_END", navigationEnd.type());
        assertEquals("Steve", navigationEnd.name());
        assertEquals("farmer", navigationEnd.role());
    }

    private NpcTelemetryEvent event(String type, long tick) {
        UUID uuid = UUID.randomUUID();
        NpcTelemetryPosition npc = new NpcTelemetryPosition("world", 1, 64, 2, 1.25, 64.0, 2.75, 90.0f, 0.0f);
        NpcTelemetryPosition target = new NpcTelemetryPosition("world", 5, 64, 6, 5.5, 64.0, 6.5, 0.0f, 0.0f);
        NpcTelemetryNavigation navigation = new NpcTelemetryNavigation(true, "world", target, "AStarNavigationStrategy", "present", "[VillageRouteExaminer]", "CITIZENS", 102.0f, -1, 1.5, 1.5, "COMPLETED", 20L);
        NpcTelemetryBlockProbe obstacle = NpcTelemetryBlockProbe.classify(
                "front", "world", 2, 64, 2, Material.STONE, true, false, true);
        return new NpcTelemetryEvent(1, type, uuid, "Steve", "farmer", "world", npc, target,
                "GOING_TO_PLOT", "GOING_TO_PLOT", navigation, "present", obstacle,
                new NpcTelemetrySemanticPoint("PLOT", "plot", "world", target), List.of(obstacle), tick, 123456789L);
    }

    private Block block(World world, int x, int y, int z, Material material, boolean passable) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(passable);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        Block support = mock(Block.class);
        when(support.getWorld()).thenReturn(world);
        when(support.getX()).thenReturn(x);
        when(support.getY()).thenReturn(y - 1);
        when(support.getZ()).thenReturn(z);
        when(support.getType()).thenReturn(Material.DIRT_PATH);
        when(support.isPassable()).thenReturn(false);
        when(block.getRelative(0, -1, 0)).thenReturn(support);
        return block;
    }
}
