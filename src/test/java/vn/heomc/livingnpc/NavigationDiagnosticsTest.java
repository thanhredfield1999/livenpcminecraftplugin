package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.List;
import java.util.logging.Logger;
import net.citizensnpcs.api.ai.event.CancelReason;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathStrategy;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class NavigationDiagnosticsTest {
    private final NavigationDiagnostics diagnostics = new NavigationDiagnostics(Logger.getAnonymousLogger());

    @Test
    void rejectsTargetsBeyondCitizensEffectiveRange() {
        World world = mock(World.class);
        Location current = new Location(world, 0.0, 64.0, 0.0);

        assertTrue(diagnostics.targetInRange(current, new Location(world, 102.0, 64.0, 0.0), 102.3f));
        assertFalse(diagnostics.targetInRange(current, new Location(world, 123.0, 64.0, 0.0), 102.3f));
        assertFalse(diagnostics.targetInRange(
                current, new Location(mock(World.class), 1.0, 64.0, 0.0), 102.3f));
    }

    @Test
    void normalizesCitizensCompletionReason() {
        assertEquals("COMPLETED", NavigationDiagnostics.reasonName(null));
        assertEquals("STUCK", NavigationDiagnostics.reasonName(CancelReason.STUCK));
        assertEquals("REPLACE", NavigationDiagnostics.reasonName(CancelReason.REPLACE));
    }


    @Test
    void appliesLegMarginsToCitizensActiveNavigation() {
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters active = mock(NavigatorParameters.class);
        Location target = mock(Location.class);
        when(navigator.getLocalParameters()).thenReturn(active);
        when(active.distanceMargin(0.75)).thenReturn(active);
        when(active.pathDistanceMargin(0.75)).thenReturn(active);

        assertEquals(active, diagnostics.activeParametersAfterTarget(navigator, target, 0.75));

        var ordered = inOrder(navigator, active);
        ordered.verify(navigator).getLocalParameters();
        ordered.verify(active).distanceMargin(0.75);
        ordered.verify(active).pathDistanceMargin(0.75);
    }

    @Test
    void structuredLogContainsExactDeltasMarginsAndPathfinder() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        UUID uuid = UUID.randomUUID();

        String message = NavigationDiagnostics.structuredMessage(
                uuid, "RANCH_ENTER", "STUCK",
                new Location(world, 45.514, -60.0625, -14.3877),
                new Location(world, 45.5, -60.0, -12.5),
                new Location(world, 45.5, -59.0, -12.5),
                1.5, 1.5, "CITIZENS", 102.29368f, -1,
                "AStarNavigationStrategy", "absent", "[DoorExaminer,VillageRouteExaminer]", 10L);

        assertTrue(message.startsWith("NPC_NAV_END uuid=" + uuid));
        assertTrue(message.contains("operation=RANCH_ENTER reason=STUCK"));
        assertTrue(message.contains("deltaX=0.0140"));
        assertTrue(message.contains("deltaY=-0.0625"));
        assertTrue(message.contains("deltaZ=-1.8877"));
        assertTrue(message.contains("horizontal=1.8878"));
        assertTrue(message.contains("distanceMargin=1.5000 pathMargin=1.5000"));
        assertTrue(message.contains("citizensTarget=StillCliff:45.5000,-59.0000,-12.5000"));
        assertTrue(message.contains("pathfinder=CITIZENS range=102.2937 stationaryTicks=-1"));
        assertTrue(message.contains("strategy=AStarNavigationStrategy path=absent"));
        assertTrue(message.contains("examiners=[DoorExaminer,VillageRouteExaminer] elapsedTicks=10"));
    }

    @Test
    void callbackDiagnosticsTolerateAnUnavailableCitizensPath() {
        PathStrategy strategy = mock(PathStrategy.class);
        when(strategy.getPath()).thenThrow(new IllegalStateException("path unavailable"));

        assertEquals("unavailable", NavigationDiagnostics.pathState(strategy));
        assertEquals("none", NavigationDiagnostics.pathState(null));
    }

    @Test
    void classifiesCitizensPathWithoutTraversingIt() {
        PathStrategy strategy = mock(PathStrategy.class);

        when(strategy.getPath()).thenReturn(null);
        assertEquals("absent", NavigationDiagnostics.pathState(strategy));
        when(strategy.getPath()).thenReturn(List.of());
        assertEquals("empty", NavigationDiagnostics.pathState(strategy));
        when(strategy.getPath()).thenReturn(List.of(new org.bukkit.util.Vector(1, 2, 3)));
        assertEquals("present", NavigationDiagnostics.pathState(strategy));
    }

    @Test
    void stalledNavigationGeometryCapturesExactFeetSupportPoseAndTargetDirection() {
        World world = mock(World.class);
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block support = mock(Block.class);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(support);
        when(feet.getX()).thenReturn(60);
        when(feet.getY()).thenReturn(-60);
        when(feet.getZ()).thenReturn(-14);
        when(feet.getType()).thenReturn(Material.AIR);
        when(head.getType()).thenReturn(Material.AIR);
        when(support.getType()).thenReturn(Material.DIRT_PATH);
        Location current = new Location(world, 60.075, -60.0, -13.5021, 135.0f, 0.0f);
        Location target = new Location(world, 63.5, -60.0, -10.5);

        String geometry = NavigationDiagnostics.stalledGeometry(current, target);

        assertEquals(" feetBlock=60,-60,-14 feet=AIR head=AIR support=DIRT_PATH"
                + " local=0.0750,0.0000,0.4979 yaw=135.0000 pitch=0.0000"
                + " targetBlock=63,-60,-11 targetDirection=0.7520,0.0000,0.6592", geometry);
    }

    @Test
    void normalNavigationEndDoesNotReadOrLogStalledGeometry() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        Location current = new Location(world, 1.0, 64.0, 1.0);
        Location target = new Location(world, 2.0, 64.0, 2.0);

        String message = NavigationDiagnostics.structuredMessage(
                UUID.randomUUID(), "FARM_CROP", "COMPLETED", current, target, target,
                1.5, 1.5, "CITIZENS", 102.0f, -1,
                "AStarNavigationStrategy", "present", "[VillageRouteExaminer]", 20L);

        assertFalse(message.contains("feetBlock="));
        org.mockito.Mockito.verify(world, org.mockito.Mockito.never())
                .getBlockAt(org.mockito.ArgumentMatchers.any(Location.class));
    }

    @Test
    void pluginTimeoutIncludesStalledGeometry() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        Block block = mock(Block.class);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        when(block.getRelative(0, 1, 0)).thenReturn(block);
        when(block.getRelative(0, -1, 0)).thenReturn(block);
        when(block.getType()).thenReturn(Material.AIR);

        String message = NavigationDiagnostics.structuredMessage(
                UUID.randomUUID(), "FARM_CROP", "PLUGIN",
                new Location(world, 1.0, 64.0, 1.0), new Location(world, 2.0, 64.0, 2.0), null,
                1.5, 1.5, "CITIZENS", 102.0f, -1,
                "AStarNavigationStrategy", "present", "[VillageRouteExaminer]", 400L);

        assertTrue(message.contains("feetBlock="));
    }
}
