package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DoubleDoorListenerTest {
    @Test
    void allowsDoorInteractionOnlyWhenNpcIsNearby() {
        World world = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        assertTrue(DoubleDoorListener.withinOpeningRange(new Location(world, 10.5, 64, 18.5), door));
        assertFalse(DoubleDoorListener.withinOpeningRange(new Location(world, 10.5, 64, 17.5), door));
    }

    @Test
    void rejectsMissingOrCrossWorldLocations() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        assertFalse(DoubleDoorListener.withinOpeningRange(null, door));
        assertFalse(DoubleDoorListener.withinOpeningRange(new Location(otherWorld, 10.5, 64, 20.5), door));
    }

    @Test
    void centresPassageOnOppositeSidesOfDoor() {
        World world = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        DoubleDoorListener.DoorSides north = DoubleDoorListener.doorSides(
                new Location(world, 10.5, 64, 18.5), door, BlockFace.NORTH);
        DoubleDoorListener.DoorSides south = DoubleDoorListener.doorSides(
                new Location(world, 10.5, 64, 22.5), door, BlockFace.NORTH);

        assertTrue(north.before().equals(new Location(world, 10.5, 64, 19.5)));
        assertTrue(north.after().equals(new Location(world, 10.5, 64, 21.5)));
        assertTrue(south.before().equals(new Location(world, 10.5, 64, 21.5)));
        assertTrue(south.after().equals(new Location(world, 10.5, 64, 19.5)));
    }

    @Test
    void doubleDoorPassageUsesTheLeafClosestToTheNpc() {
        World world = mock(World.class);
        Location npc = new Location(world, 8.7, 64, 18.5);
        Location eventLeaf = new Location(world, 10, 64, 20);
        Location partnerLeaf = new Location(world, 9, 64, 20);

        assertTrue(DoubleDoorListener.closestDoorLeaf(npc, eventLeaf, partnerLeaf).equals(partnerLeaf));
        assertTrue(DoubleDoorListener.closestDoorLeaf(
                new Location(world, 11.3, 64, 18.5), eventLeaf, partnerLeaf).equals(eventLeaf));
    }

    @Test
    void findsClosedDoorIntersectingNpcHitboxButNotNearbyDoor() {
        World world = mock(World.class);
        Block air = mock(Block.class);
        Block obstructing = mock(Block.class);
        Block nearby = mock(Block.class);
        Door closedDoor = mock(Door.class);
        when(closedDoor.isOpen()).thenReturn(false);
        when(air.getBlockData()).thenReturn(mock(org.bukkit.block.data.BlockData.class));
        when(obstructing.getBlockData()).thenReturn(closedDoor);
        when(nearby.getBlockData()).thenReturn(closedDoor);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(air);
        when(world.getBlockAt(1, -57, 2)).thenReturn(obstructing);
        when(world.getBlockAt(2, -57, 2)).thenReturn(nearby);

        assertTrue(DoubleDoorListener.findObstructingDoor(
                new Location(world, 1.30, -57.0, 2.4875)).orElseThrow() == obstructing);
        assertTrue(DoubleDoorListener.findObstructingDoor(
                new Location(world, 0.60, -57.0, 2.4875)).isEmpty());
    }

    @Test
    void recoveryInsideClosedDoorSkipsUnreachableApproachTarget() {
        World world = mock(World.class);
        Location before = new Location(world, 1.5, -57.0, 1.5);
        Location after = new Location(world, 1.5, -57.0, 3.5);
        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(before, after);

        assertTrue(DoubleDoorListener.passageStartTarget(sides, false).equals(before));
        assertTrue(DoubleDoorListener.passageStartTarget(sides, true).equals(after));
    }

    @Test
    void passageTargetAcceptsCitizensEntityFootOffset() {
        World world = mock(World.class);
        Location target = new Location(world, 1.5, -57.0, 3.5);

        assertTrue(DoubleDoorListener.passageTargetReached(
                new Location(world, 1.30, -56.4375, 3.4875), target));
        assertFalse(DoubleDoorListener.passageTargetReached(
                new Location(world, 1.30, -55.9, 3.4875), target));
        assertFalse(DoubleDoorListener.passageTargetReached(
                new Location(world, 1.90, -56.4375, 3.4875), target));
    }

    @Test
    void reachingApproachPointWinsWhenCitizensHasJustClearedCompletedTarget() {
        World world = mock(World.class);
        Location approach = new Location(world, -18.5, -60.0, -67.5);
        Location current = new Location(world, -18.70, -59.4375, -67.49);

        assertEquals(
                DoubleDoorListener.PassageTargetState.REACHED,
                DoubleDoorListener.passageTargetState(current, null, approach));
    }

    @Test
    void anotherOwnersTargetWinsEvenWhenNpcHasReachedDoorTarget() {
        World world = mock(World.class);
        Location approach = new Location(world, -18.5, -60.0, -67.5);
        Location replacement = new Location(world, 20.5, -60.0, 20.5);

        assertEquals(
                DoubleDoorListener.PassageTargetState.PREEMPTED,
                DoubleDoorListener.passageTargetState(approach.clone(), replacement, approach));
    }

    @Test
    void targetClearedByCitizensIsNotConfusedWithAnotherOwnersTarget() {
        World world = mock(World.class);
        Location current = new Location(world, 18.5, 64, 20.5);
        Location expected = new Location(world, 19.5, 64, 20.5);
        Location replacement = new Location(world, 30.5, 64, 30.5);

        assertEquals(
                DoubleDoorListener.PassageTargetState.TARGET_CLEARED,
                DoubleDoorListener.passageTargetState(current, null, expected));
        assertEquals(
                DoubleDoorListener.PassageTargetState.PREEMPTED,
                DoubleDoorListener.passageTargetState(current, replacement, expected));
        assertTrue(DoubleDoorListener.shouldRestoreOriginalTarget(
                DoubleDoorListener.PassageTargetState.TARGET_CLEARED));
        assertFalse(DoubleDoorListener.shouldRestoreOriginalTarget(
                DoubleDoorListener.PassageTargetState.PREEMPTED));
    }

    @Test
    void failedPassageStartupRollsBackActiveStateAndNavigationLease() {
        LivingNpcPlugin plugin = mock(LivingNpcPlugin.class);
        FarmerManager manager = mock(FarmerManager.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(plugin.manager()).thenReturn(manager);
        when(manager.navigationLeases()).thenReturn(leases);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DoubleDoorListenerTest"));
        DoubleDoorListener listener = new DoubleDoorListener(plugin);

        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters local = mock(NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        NavigatorParameters originalParameters = mock(
                NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        World world = mock(World.class);
        Location originalTarget = new Location(world, 20.5, 64, 20.5);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getTargetAsLocation()).thenReturn(originalTarget);
        when(navigator.getLocalParameters()).thenReturn(local);
        when(local.clone()).thenReturn(originalParameters);
        when(originalParameters.examiners()).thenReturn(new BlockExaminer[0]);
        doThrow(new IllegalStateException("setTarget failed"))
                .doNothing()
                .when(navigator).setTarget(any(Location.class));

        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(world, 1.5, 64, 1.5),
                new Location(world, 1.5, 64, 3.5));

        assertDoesNotThrow(() -> listener.startPassage(npc, mock(Block.class), sides, false));
        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
        assertFalse(listener.hasActivePassage(npcUuid));
        verify(navigator, times(2)).setTarget(any(Location.class));
    }

    @Test
    void failedPassageStartupCancelsPassageNavigationWhenOriginalTargetWasNull() {
        LivingNpcPlugin plugin = mock(LivingNpcPlugin.class);
        FarmerManager manager = mock(FarmerManager.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(plugin.manager()).thenReturn(manager);
        when(manager.navigationLeases()).thenReturn(leases);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DoubleDoorListenerTest"));
        DoubleDoorListener listener = new DoubleDoorListener(plugin);

        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters local = mock(NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        NavigatorParameters originalParameters = mock(
                NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        World world = mock(World.class);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getTargetAsLocation()).thenReturn(null);
        when(navigator.getLocalParameters()).thenReturn(local);
        when(local.clone()).thenReturn(originalParameters);
        doThrow(new IllegalStateException("parameters failed")).when(local).clearExaminers();

        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(world, 1.5, 64, 1.5),
                new Location(world, 1.5, 64, 3.5));

        assertDoesNotThrow(() -> listener.startPassage(npc, mock(Block.class), sides, false));
        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
        assertFalse(listener.hasActivePassage(npcUuid));
        verify(navigator).setTarget(sides.before());
        verify(navigator).cancelNavigation();
    }

    @Test
    void cleanupFailureCannotLeakTheDoorNavigationLease() {
        NavigationLeaseManager leases = new NavigationLeaseManager();
        UUID npcUuid = UUID.randomUUID();
        assertTrue(DoubleDoorListener.claimPassage(leases, npcUuid));

        assertThrows(IllegalStateException.class, () -> DoubleDoorListener.releasePassageAfter(
                leases, npcUuid, () -> { throw new IllegalStateException("close failed"); }));

        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
    }

    @Test
    void abortWithNullOriginalTargetCancelsPassageNavigationBeforeRelease() throws Exception {
        DoubleDoorListener listener = listener();
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        World world = mock(World.class);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getStoredLocation()).thenReturn(new Location(world, 1.5, 64, 1.5));
        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(world, 1.5, 64, 1.5), new Location(world, 1.5, 64, 3.5));
        Object passage = newPassage(sourceBlock(world), sides, null,
                mock(NavigatorParameters.class));
        NavigationLeaseManager leases = listenerLeases(listener);
        assertTrue(DoubleDoorListener.claimPassage(leases, npcUuid));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertNullFailure(invokeAbort(listener, npc, passage, "ABORTED_TEST"));
        }

        verify(navigator).cancelNavigation();
        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
    }

    @Test
    void closeFailureDoesNotSkipPassageRestoreAndKeepsPrimaryWithSuppressed() throws Exception {
        DoubleDoorListener listener = listener();
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters local = mock(NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        NavigatorParameters originalParameters = mock(
                NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        World world = mock(World.class);
        Location originalTarget = new Location(world, 20.5, 64, 20.5);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getLocalParameters()).thenReturn(local);
        when(originalParameters.examiners()).thenReturn(new BlockExaminer[0]);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getStoredLocation()).thenReturn(new Location(world, 1.5, 64, 1.5));
        doThrow(new IllegalStateException("restore failed"))
                .when(navigator).setTarget(any(Location.class));
        Block openedBlock = mock(Block.class);
        when(openedBlock.getType()).thenReturn(Material.IRON_DOOR);
        Door door = mock(Door.class);
        when(door.isOpen()).thenReturn(true);
        when(openedBlock.getBlockData()).thenReturn(door);
        doThrow(new IllegalStateException("close failed")).when(openedBlock).setBlockData(any());
        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(world, 1.5, 64, 1.5), new Location(world, 1.5, 64, 3.5));
        Object passage = newPassage(sourceBlock(world), sides, originalTarget, originalParameters);
        addOpenedDoor(passage, openedBlock, Material.IRON_DOOR, "closed");
        NavigationLeaseManager leases = listenerLeases(listener);
        assertTrue(DoubleDoorListener.claimPassage(leases, npcUuid));

        RuntimeException failure;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            failure = invokeAbort(listener, npc, passage, "ABORTED_TEST");
        }

        assertNotNull(failure);
        assertEquals("close failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("restore failed", failure.getSuppressed()[0].getMessage());
        verify(navigator).setTarget(originalTarget);
        verify(navigator).getLocalParameters();
        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
    }

    @Test
    void finishPassageRestoresParametersEvenWhenTargetNearPassageEndpoint() throws Exception {
        DoubleDoorListener listener = listener();
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters local = mock(NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        NavigatorParameters originalParameters = mock(
                NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        World world = mock(World.class);
        Location originalTarget = new Location(world, 1.6, 64, 3.3);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getLocalParameters()).thenReturn(local);
        when(originalParameters.examiners()).thenReturn(new BlockExaminer[0]);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getStoredLocation()).thenReturn(new Location(world, 1.5, 64, 1.5));
        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(world, 1.5, 64, 1.5), new Location(world, 1.5, 64, 3.5));
        Object passage = newPassage(sourceBlock(world), sides, originalTarget, originalParameters);
        NavigationLeaseManager leases = listenerLeases(listener);
        assertTrue(DoubleDoorListener.claimPassage(leases, npcUuid));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertNullFailure(invokeFinish(listener, npc, passage));
        }

        verify(navigator).setTarget(originalTarget);
        verify(navigator).getLocalParameters();
        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
    }

    @Test
    void preemptedCleanupDoesNotTouchNewOwnerNavigation() throws Exception {
        DoubleDoorListener listener = listener();
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters local = mock(NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        NavigatorParameters originalParameters = mock(
                NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        World world = mock(World.class);
        Location originalTarget = new Location(world, 20.5, 64, 20.5);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getLocalParameters()).thenReturn(local);
        when(navigator.isNavigating()).thenReturn(true);
        when(originalParameters.examiners()).thenReturn(new BlockExaminer[0]);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getStoredLocation()).thenReturn(new Location(world, 1.5, 64, 1.5));
        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(world, 1.5, 64, 1.5), new Location(world, 1.5, 64, 3.5));
        Object passage = newPassage(sourceBlock(world), sides, originalTarget, originalParameters);
        NavigationLeaseManager leases = listenerLeases(listener);
        assertTrue(DoubleDoorListener.claimPassage(leases, npcUuid));
        assertTrue(leases.claim(npcUuid, "new-owner", 100, null));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertNullFailure(invokeAbort(listener, npc, passage, "ABORTED_TEST"));
        }

        verify(navigator, never()).setTarget(any(Location.class));
        verify(navigator, never()).cancelNavigation();
        verify(navigator, never()).getLocalParameters();
        assertTrue(leases.heldBy(npcUuid, "new-owner"));
    }

    @Test
    void worldAbortDoesNotRestoreOriginalTargetAcrossWorldsAndCancelsPassageNavigation()
            throws Exception {
        DoubleDoorListener listener = listener();
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters local = mock(NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        NavigatorParameters originalParameters = mock(
                NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        World worldA = mock(World.class);
        World worldB = mock(World.class);
        Location originalTarget = new Location(worldA, 20.5, 64, 20.5);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getLocalParameters()).thenReturn(local);
        when(originalParameters.examiners()).thenReturn(new BlockExaminer[0]);
        when(navigator.isNavigating()).thenReturn(true);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getStoredLocation()).thenReturn(new Location(worldB, 1.5, 64, 1.5));
        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(worldB, 1.5, 64, 1.5), new Location(worldB, 1.5, 64, 3.5));
        Object passage = newPassage(sourceBlock(worldB), sides, originalTarget,
                originalParameters);
        NavigationLeaseManager leases = listenerLeases(listener);
        assertTrue(DoubleDoorListener.claimPassage(leases, npcUuid));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertNullFailure(invokeAbort(listener, npc, passage, "ABORTED_WORLD"));
        }

        verify(navigator, never()).setTarget(any(Location.class));
        verify(navigator).cancelNavigation();
        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
        assertFalse(listener.hasActivePassage(npcUuid));
    }

    @Test
    void rollbackPassageStartupCancelsNavigationWhenOriginalTargetRestoreFails() throws Exception {
        DoubleDoorListener listener = listener();
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters local = mock(NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        NavigatorParameters originalParameters = mock(
                NavigatorParameters.class, org.mockito.Answers.RETURNS_SELF);
        World world = mock(World.class);
        Location originalTarget = new Location(world, 20.5, 64, 20.5);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getTargetAsLocation()).thenReturn(originalTarget);
        when(navigator.getLocalParameters()).thenReturn(local);
        when(local.clone()).thenReturn(originalParameters);
        when(originalParameters.examiners()).thenReturn(new BlockExaminer[0]);
        doNothingThenThrow(new IllegalStateException("restore failed"))
                .when(navigator).setTarget(any(Location.class));
        doThrow(new IllegalStateException("parameters failed")).when(local).clearExaminers();
        DoubleDoorListener.DoorSides sides = new DoubleDoorListener.DoorSides(
                new Location(world, 1.5, 64, 1.5), new Location(world, 1.5, 64, 3.5));
        NavigationLeaseManager leases = listenerLeases(listener);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertDoesNotThrow(() -> listener.startPassage(npc, mock(Block.class), sides, false));
        }

        verify(navigator).cancelNavigation();
        assertFalse(leases.heldBy(npcUuid, DoubleDoorListener.NAVIGATION_OWNER));
        assertFalse(listener.hasActivePassage(npcUuid));
    }

    private static void assertNullFailure(RuntimeException failure) {
        assertTrue(failure == null);
    }

    private static Block sourceBlock(World world) {
        Block source = mock(Block.class);
        when(source.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("StillCliff");
        return source;
    }

    private static org.mockito.stubbing.Stubber doNothingThenThrow(RuntimeException exception) {
        return org.mockito.Mockito.doNothing().doThrow(exception);
    }

    private static DoubleDoorListener listener() {
        LivingNpcPlugin plugin = mock(LivingNpcPlugin.class);
        FarmerManager manager = mock(FarmerManager.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(plugin.manager()).thenReturn(manager);
        when(manager.navigationLeases()).thenReturn(leases);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DoubleDoorListenerTest"));
        return new DoubleDoorListener(plugin);
    }

    private static NavigationLeaseManager listenerLeases(DoubleDoorListener listener) throws Exception {
        Field field = DoubleDoorListener.class.getDeclaredField("navigationLeases");
        field.setAccessible(true);
        return (NavigationLeaseManager) field.get(listener);
    }

    private static Object newPassage(Block source, DoubleDoorListener.DoorSides sides,
            Location originalTarget, NavigatorParameters originalParameters) throws Exception {
        Class<?> cls = Class.forName("vn.heomc.livingnpc.DoubleDoorListener$DoorPassage");
        Constructor<?> ctor = cls.getDeclaredConstructor(
                Block.class, DoubleDoorListener.DoorSides.class, Location.class,
                NavigatorParameters.class);
        ctor.setAccessible(true);
        return ctor.newInstance(source, sides, originalTarget, originalParameters);
    }

    private static void addOpenedDoor(Object passage, Block block, Material material,
            String closedState) throws Exception {
        Class<?> cls = Class.forName("vn.heomc.livingnpc.DoubleDoorListener$OpenedDoor");
        Constructor<?> ctor = cls.getDeclaredConstructor(Block.class, Material.class, String.class);
        ctor.setAccessible(true);
        Object opened = ctor.newInstance(block, material, closedState);
        Field field = Class.forName("vn.heomc.livingnpc.DoubleDoorListener$DoorPassage")
                .getDeclaredField("openedDoors");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> openedDoors = (List<Object>) field.get(passage);
        openedDoors.add(opened);
    }

    private static RuntimeException invokeAbort(DoubleDoorListener listener, NPC npc,
            Object passage, String result) throws Exception {
        return invokeTerminal(listener, "abortPassage",
                new Class<?>[] { NPC.class, passageClass(), String.class },
                new Object[] { npc, passage, result });
    }

    private static RuntimeException invokeFinish(DoubleDoorListener listener, NPC npc,
            Object passage) throws Exception {
        return invokeTerminal(listener, "finishPassage",
                new Class<?>[] { NPC.class, passageClass() },
                new Object[] { npc, passage });
    }

    private static RuntimeException invokeTerminal(DoubleDoorListener listener, String name,
            Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method method = DoubleDoorListener.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            method.invoke(listener, args);
            return null;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) return runtime;
            throw new RuntimeException(cause);
        }
    }

    private static Class<?> passageClass() throws Exception {
        return Class.forName("vn.heomc.livingnpc.DoubleDoorListener$DoorPassage");
    }
}
