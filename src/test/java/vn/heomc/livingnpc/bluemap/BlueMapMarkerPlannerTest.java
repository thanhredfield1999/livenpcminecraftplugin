package vn.heomc.livingnpc.bluemap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.heomc.livingnpc.NpcTelemetryEvent;
import vn.heomc.livingnpc.NpcTelemetryAccount;
import vn.heomc.livingnpc.NpcTelemetryActivity;
import vn.heomc.livingnpc.NpcTelemetryEconomySnapshot;
import vn.heomc.livingnpc.NpcTelemetryInventoryItem;
import vn.heomc.livingnpc.NpcTelemetryGate;
import vn.heomc.livingnpc.NpcTelemetryNavigation;
import vn.heomc.livingnpc.NpcTelemetryPosition;
import vn.heomc.livingnpc.NpcTelemetryRoleProduction;
import vn.heomc.livingnpc.NpcTelemetrySemanticPoint;
import vn.heomc.livingnpc.NpcTelemetrySnapshot;
import vn.heomc.livingnpc.NpcTelemetryVillageEconomy;

class BlueMapMarkerPlannerTest {
    @Test
    void configDefaultsKeepBlueMapMarkersDisabled() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();

        BlueMapSettings settings = BlueMapSettings.load(yaml);

        assertFalse(settings.enabled());
        assertEquals(100L, settings.intervalTicks());
        assertEquals(600L, settings.staleTicks());
    }

    @Test
    void latestEventPerNpcBuildsStableEscapedNpcAndSemanticMarkers() {
        UUID npc = UUID.randomUUID();
        NpcTelemetryEvent old = event(npc, "Alex", "farmer", "WORKING", "WORKING", point(1.0, 64.0, 1.0), null, 10L);
        NpcTelemetryEvent latest = event(npc, "<Steve>", "rancher", "GOING_TO_PLOT", "APPROACH", point(2.5, 65.0, 3.5),
                new NpcTelemetrySemanticPoint("GATE", "north <gate>", "world", point(4.5, 65.0, 5.5)), 20L);
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 8, 2, List.of(old, latest));

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(snapshot, 30L, 600L);

        assertEquals("livingnpc-observatory", BlueMapMarkerPlanner.MARKER_SET_ID);
        assertEquals(2, plan.markers().size());
        BlueMapMarkerSpec npcMarker = plan.markers().get("livingnpc-npc-" + npc);
        assertEquals("world", npcMarker.world());
        assertEquals(2.5, npcMarker.position().x());
        assertTrue(npcMarker.label().contains("&lt;Steve&gt;"));
        assertTrue(npcMarker.label().contains("rancher"));
        assertTrue(npcMarker.label().contains("GOING_TO_PLOT"));
        assertTrue(npcMarker.label().contains("APPROACH"));
        assertNull(npcMarker.rawPayload());
        BlueMapMarkerSpec semantic = plan.markers().get("livingnpc-semantic-" + npc + "-gate-north-gate");
        assertEquals("north &lt;gate&gt;", semantic.label());
        assertEquals("world", semantic.world());
    }

    @Test
    void staleEventsAreRemovedFromActivePlanAndReportedAsMarkerIds() {
        UUID stale = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 8, 2, List.of(
                event(stale, "Old", "farmer", "WORKING", "WORKING", point(1.0, 64.0, 1.0), null, 100L),
                event(fresh, "Fresh", "fisher", "GOING_TO_FISHING_SPOT", "FINAL", point(2.0, 64.0, 2.0), null, 650L)));

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(snapshot, 700L, 200L);

        assertFalse(plan.markers().containsKey("livingnpc-npc-" + stale));
        assertTrue(plan.staleMarkerIds().contains("livingnpc-npc-" + stale));
        assertTrue(plan.staleMarkerIds().contains("livingnpc-route-" + stale));
        assertTrue(plan.markers().containsKey("livingnpc-npc-" + fresh));
    }

    @Test
    void plannerBuildsSanitizedSkinIconAndRouteDiagnosisFromTelemetry() {
        UUID npc = UUID.randomUUID();
        NpcTelemetryPosition current = point(2.5, 65.0, 3.5);
        NpcTelemetryPosition target = point(10.5, 65.0, 3.5);
        NpcTelemetryNavigation navigation = new NpcTelemetryNavigation(
                true, "world", target, "AStar", "absent", "[]", "CITIZENS", 64.0f, 20,
                0.75, 0.75, "ACTIVE", 4L);
        NpcTelemetryEvent event = new NpcTelemetryEvent(1, "ACTION", npc, "Steve", "farmer", "world",
                current, target, "GOING_TO_PLOT", "APPROACH", navigation, "absent", null, null,
                List.of(), 20L, 1L, "Steve");

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(
                new NpcTelemetrySnapshot(1, 8, 1, List.of(event)), 30L, 600L);

        BlueMapMarkerSpec npcMarker = plan.markers().get("livingnpc-npc-" + npc);
        assertTrue(npcMarker.detail().contains("GOING_TO_PLOT"));
        assertTrue(npcMarker.detail().contains("APPROACH"));
        assertEquals("https://mc-heads.net/avatar/Steve/64.png", npcMarker.iconUrl());
        assertEquals(64, npcMarker.iconWidth());
        assertEquals(64, npcMarker.iconHeight());
        assertFalse(npcMarker.label().contains("!"));
        assertTrue(npcMarker.detail().contains("path=absent"));
        assertTrue(npcMarker.detail().contains("action=Đang tìm đường"));
        BlueMapMarkerSpec route = plan.markers().get("livingnpc-route-" + npc);
        assertEquals(target.x(), route.routeTarget().x());
        assertEquals("#f59e0b", route.routeColor());
        assertTrue(route.label().contains("Đang tìm đường"));
    }

    @Test
    void plannerRejectsUnsafeSkinAndOnlyBuildsRouteInsideSameWorld() {
        UUID npc = UUID.randomUUID();
        NpcTelemetryPosition current = point(2.5, 65.0, 3.5);
        NpcTelemetryPosition target = new NpcTelemetryPosition("nether", 10, 65, 3, 10.5, 65.0, 3.5, 0.0f, 0.0f);
        NpcTelemetryEvent event = new NpcTelemetryEvent(1, "ACTION", npc, "Not safe!", "farmer", "world",
                current, target, "STUCK", "APPROACH", null, "present", null, null, List.of(), 20L, 1L);

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(
                new NpcTelemetrySnapshot(1, 8, 1, List.of(event)), 30L, 600L);

        BlueMapMarkerSpec marker = plan.markers().get("livingnpc-npc-" + npc);
        assertEquals("https://mc-heads.net/avatar/MHF_Steve/64.png", marker.iconUrl());
        assertFalse(plan.markers().containsKey("livingnpc-route-" + npc));
        assertTrue(marker.detail().contains("STUCK"));
    }

    @Test
    void plannerRendersEscapedNpcAccountDetailsOrUnavailableAccount() {
        UUID present = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        NpcTelemetryAccount account = new NpcTelemetryAccount(125L, "<minor>", 12,
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(index -> new NpcTelemetryInventoryItem(index == 0 ? "<wheat>" : "item-" + index,
                                index == 0 ? 7 : 1))
                        .toList());
        NpcTelemetryEvent withAccount = new NpcTelemetryEvent(1, "ACTION", present, "<Steve>", "farmer", "world",
                point(1.0, 64.0, 1.0), null, "WORKING", "WORKING", null, "empty", null, null,
                List.of(), 20L, 1L, null, account);
        NpcTelemetryEvent withoutAccount = new NpcTelemetryEvent(1, "ACTION", missing, "Alex", "fisher", "world",
                point(2.0, 64.0, 2.0), null, "WORKING", "WORKING", null, "empty", null, null,
                List.of(), 20L, 1L, null, null);

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(
                new NpcTelemetrySnapshot(1, 8, 2L, List.of(withAccount, withoutAccount)), 30L, 600L);

        BlueMapMarkerSpec presentMarker = plan.markers().get(BlueMapMarkerPlanner.npcMarkerId(present));
        assertTrue(presentMarker.label().contains("NPC &lt;Steve&gt; | job=farmer"));
        assertTrue(presentMarker.detail().contains("job=farmer"));
        assertTrue(presentMarker.detail().contains("money=125 &lt;minor&gt;"));
        assertTrue(presentMarker.detail().contains("inventoryTotal=12"));
        assertTrue(presentMarker.detail().contains("&lt;wheat&gt;=7"));
        assertTrue(presentMarker.detail().contains("item-7=1"));
        assertFalse(presentMarker.detail().contains("item-8=1"));
        assertTrue(plan.markers().get(BlueMapMarkerPlanner.npcMarkerId(missing)).detail()
                .contains("account=unavailable"));
    }

    @Test
    void plannerUsesRouteColorsFromNavigationTelemetry() {
        NpcTelemetryPosition current = point(2.5, 65.0, 3.5);
        NpcTelemetryPosition target = point(10.5, 65.0, 3.5);
        NpcTelemetryNavigation present = new NpcTelemetryNavigation(
                true, "world", target, "AStar", "present", "[]", "CITIZENS", 64.0f, 20,
                0.75, 0.75, "ACTIVE", 4L);
        NpcTelemetryNavigation stopped = new NpcTelemetryNavigation(
                false, "world", target, "none", "empty", "[]", "CITIZENS", 64.0f, 0,
                0.75, 0.75, "COMPLETED", 4L);
        UUID stuck = UUID.randomUUID();
        UUID moving = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 8, 3, List.of(
                new NpcTelemetryEvent(1, "ACTION", stuck, "Stuck", "farmer", "world", current, target,
                        "STUCK", "APPROACH", present, "present", null, null, List.of(), 20L, 1L),
                new NpcTelemetryEvent(1, "ACTION", moving, "Moving", "farmer", "world", current, target,
                        "GOING", "APPROACH", present, "present", null, null, List.of(), 20L, 1L),
                new NpcTelemetryEvent(1, "ACTION", idle, "Idle", "farmer", "world", current, target,
                        "IDLE", "WAITING", stopped, "empty", null, null, List.of(), 20L, 1L)));

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(snapshot, 30L, 600L);

        assertEquals("#ef4444", plan.markers().get(BlueMapMarkerPlanner.routeMarkerId(stuck)).routeColor());
        assertEquals("#22c55e", plan.markers().get(BlueMapMarkerPlanner.routeMarkerId(moving)).routeColor());
        assertEquals("#64748b", plan.markers().get(BlueMapMarkerPlanner.routeMarkerId(idle)).routeColor());
    }

    @Test
    void oldMarkerSpecConstructorRemainsCompatible() {
        BlueMapMarkerSpec spec = new BlueMapMarkerSpec(
                "id", "world", new BlueMapMarkerPosition(1, 2, 3), "label", "detail", false, null);

        assertNull(spec.rawPayload());
        assertNull(spec.iconUrl());
        assertNull(spec.routeTarget());
    }

    @Test
    void eventsWithoutMappedWorldOrPositionFailClosed() {
        UUID npc = UUID.randomUUID();
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 8, 1, List.of(
                new NpcTelemetryEvent(1, "ACTION", npc, "NoPos", "farmer", null,
                        null, null, "WORKING", "WORKING", null, null, null, null, List.of(), 10L, 1L)));

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(snapshot, 20L, 600L);

        assertTrue(plan.markers().isEmpty());
        assertTrue(plan.staleMarkerIds().contains("livingnpc-npc-" + npc));
    }

    @Test
    void configuredGatesBuildStablePoiMarkersForOpenClosedAndUnknownStates() {
        NpcTelemetrySnapshot snapshot = new NpcTelemetrySnapshot(1, 8, 0L, List.of(), null, null, List.of(
                new NpcTelemetryGate("village-0-world-10-64-20", "world", 10, 64, 20,
                        "OAK_FENCE_GATE", true, "OPEN", "SHARED", 99L),
                new NpcTelemetryGate("village-1-world-11-64-20", "world", 11, 64, 20,
                        "OAK_DOOR", false, "CLOSED", "FARMER", 99L),
                new NpcTelemetryGate("village-2-world-12-64-20", "world", 12, 64, 20,
                        "STONE", null, "UNKNOWN", null, 99L)));

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(snapshot, 100L, 600L);

        assertEquals(3, plan.markers().size());
        BlueMapMarkerSpec open = plan.markers().get("livingnpc-gate-village-0-world-10-64-20");
        assertEquals("world", open.world());
        assertEquals(10.5, open.position().x());
        assertTrue(open.label().contains("OPEN"));
        assertTrue(open.detail().contains("coords=world:10,64,20"));
        assertTrue(open.detail().contains("material=OAK_FENCE_GATE"));
        assertTrue(open.detail().contains("action=SHARED"));
        assertTrue(plan.markers().get("livingnpc-gate-village-1-world-11-64-20").label().contains("CLOSED"));
        assertTrue(plan.markers().get("livingnpc-gate-village-2-world-12-64-20").detail().contains("status=UNKNOWN"));
        assertTrue(plan.staleMarkerIds().isEmpty());
    }

    @Test
    void economyVillageWithLatestMatchingPositionBuildsStableEscapedSemanticPoi() {
        UUID npc = UUID.randomUUID();
        NpcTelemetryEvent event = new NpcTelemetryEvent(1, "ACTION", npc, "Steve", "farmer", "village-<a>", "world",
                point(12.5, 65.0, 20.5), null, "WORKING", "WORKING", null, "empty", null, null,
                List.of(), 50L, 1L, null, null);
        NpcTelemetryVillageEconomy village = new NpcTelemetryVillageEconomy("village-<a>", 125L, "credits",
                List.of(new NpcTelemetryInventoryItem("<wheat>", 3), new NpcTelemetryInventoryItem("carrot", 2)),
                List.of(new NpcTelemetryRoleProduction("farmer<", 4)),
                List.of(new NpcTelemetryActivity("farmer<", "Thu hoạch", "<wheat>", 3)));

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(new NpcTelemetrySnapshot(
                1, 8, 1L, List.of(event), new NpcTelemetryEconomySnapshot(List.of(village)), null, List.of()), 60L, 600L);

        BlueMapMarkerSpec marker = plan.markers().get("livingnpc-economy-village-a");
        assertEquals("world", marker.world());
        assertEquals(12.5, marker.position().x());
        assertTrue(marker.semantic());
        assertTrue(marker.label().contains("village-&lt;a&gt;"));
        assertTrue(marker.detail().contains("kind=VILLAGE_ECONOMY"));
        assertTrue(marker.detail().contains("villageId=village-&lt;a&gt;"));
        assertTrue(marker.detail().contains("balanceMinor=125"));
        assertTrue(marker.detail().contains("totalEarnedMinor=125"));
        assertTrue(marker.detail().contains("totalSpentMinor=0"));
        assertTrue(marker.detail().contains("currencyUnit=credits"));
        assertTrue(marker.detail().contains("inventoryTotal=5"));
        assertTrue(marker.detail().contains("inventory=&lt;wheat&gt;:3,carrot:2"));
        assertTrue(marker.detail().contains("roleProduction=farmer&lt;:4"));
        assertTrue(marker.detail().contains("activities=farmer&lt;:Thu hoạch:&lt;wheat&gt;:3:"));
        assertTrue(marker.detail().contains("timestampTick=50"));
    }

    @Test
    void economyVillageWithoutValidLatestMatchingPositionFailsClosed() {
        NpcTelemetryVillageEconomy village = new NpcTelemetryVillageEconomy("village-a", 125L, "minor",
                List.of(), List.of(), List.of());

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(new NpcTelemetrySnapshot(
                1, 8, 0L, List.of(), new NpcTelemetryEconomySnapshot(List.of(village)), null, List.of()), 60L, 600L);

        assertFalse(plan.markers().containsKey("livingnpc-economy-village-a"));
    }

    @Test
    void economyVillageWithInvalidLatestMatchingPositionFailsClosedInsteadOfReusingOlderPosition() {
        UUID npc = UUID.randomUUID();
        NpcTelemetryEvent positioned = new NpcTelemetryEvent(1, "ACTION", npc, "Steve", "farmer", "village-a", "world",
                point(12.5, 65.0, 20.5), null, "WORKING", "WORKING", null, "empty", null, null,
                List.of(), 50L, 1L, null, null);
        NpcTelemetryEvent latestWithoutPosition = new NpcTelemetryEvent(1, "ACTION", npc, "Steve", "farmer", "village-a", "world",
                null, null, "WORKING", "WORKING", null, "empty", null, null,
                List.of(), 60L, 1L, null, null);
        NpcTelemetryVillageEconomy village = new NpcTelemetryVillageEconomy("village-a", 125L, "minor",
                List.of(), List.of(), List.of());

        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(new NpcTelemetrySnapshot(
                1, 8, 2L, List.of(positioned, latestWithoutPosition), new NpcTelemetryEconomySnapshot(List.of(village)), null, List.of()),
                70L, 600L);

        assertFalse(plan.markers().containsKey("livingnpc-economy-village-a"));
    }

    private static NpcTelemetryEvent event(UUID npc, String name, String role, String state, String phase,
            NpcTelemetryPosition position, NpcTelemetrySemanticPoint semanticPoint, long tick) {
        return new NpcTelemetryEvent(1, "ACTION", npc, name, role, position.world(),
                position, null, state, phase, null, "present", null, semanticPoint, List.of(), tick, 1L);
    }

    private static NpcTelemetryPosition point(double x, double y, double z) {
        return new NpcTelemetryPosition("world", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                x, y, z, 0.0f, 0.0f);
    }
}
