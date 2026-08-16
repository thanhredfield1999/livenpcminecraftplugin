package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class FisherRuntimeLifecycleTest {
    @Test
    void reachingQuotaDuringTripCancelsNavigationAndReleasesLease() {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        runtime.stopForQuota();

        assertEquals(FarmerPhase.RESTING, runtime.phase());
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
        verify(navigator).cancelNavigation();
    }

    @Test
    void partialHookSetupKeepsSpawnedHookForLaterCleanup() throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Player player = mock(Player.class);
        FishHook hook = mock(FishHook.class);
        World world = mock(World.class);
        Block water = mock(Block.class);
        Levelled waterData = mock(Levelled.class);
        Location waterLocation = new Location(world, 0.5, 64.9, 0.5);
        Location standingTarget = new Location(world, 1.5, 64, 0.5);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getEntity()).thenReturn(player);
        when(player.getLocation()).thenReturn(new Location(world, 1.5, 64, 0.5));
        when(player.getEyeLocation()).thenReturn(new Location(world, 1.5, 65.6, 0.5));
        when(player.launchProjectile(eq(FishHook.class), any())).thenReturn(hook);
        when(hook.getLocation()).thenReturn(waterLocation);
        when(water.getType()).thenReturn(Material.WATER);
        when(water.getBlockData()).thenReturn(waterData);
        when(waterData.getLevel()).thenReturn(0);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(water);
        when(world.getBlockAt(any(Location.class))).thenReturn(water);
        doThrow(new IllegalStateException("hook velocity failed"))
                .when(hook).setVelocity(any());
        FisherRuntime runtime = new FisherRuntime(
                npc, mock(FarmerDefinition.class), mock(NpcEconomy.class), mock(VillageStore.class),
                new NavigationLeaseManager(), ignored -> { });
        setField(runtime, "fishingWater", waterLocation);
        setField(runtime, "standingTarget", standingTarget);

        assertFalse((boolean) invokeCastLine(runtime, 100L));

        assertSame(hook, field(runtime, "hook"));
    }

    @Test
    void failedHookRemovalRetainsHandleForIdempotentRetry() throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        FishHook hook = mock(FishHook.class);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(hook.isValid()).thenReturn(true);
        doThrow(new IllegalStateException("hook remove failed"))
                .doNothing().when(hook).remove();
        FisherRuntime runtime = new FisherRuntime(
                npc, mock(FarmerDefinition.class), mock(NpcEconomy.class), mock(VillageStore.class),
                new NavigationLeaseManager(), ignored -> { });
        setField(runtime, "hook", hook);

        assertThrows(IllegalStateException.class, runtime::releaseWorkState);
        assertSame(hook, field(runtime, "hook"));

        assertDoesNotThrow(runtime::releaseWorkState);
        verify(hook, times(2)).remove();
        assertNull(field(runtime, "hook"));
    }

    @Test
    void reachingQuotaCompletesTeardownWhenRemovingHookFails() throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        FishHook hook = mock(FishHook.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(hook.isValid()).thenReturn(true);
        doThrow(new IllegalStateException("hook remove failed")).when(hook).remove();
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "hook", hook);
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::stopForQuota);

        assertEquals("hook remove failed", failure.getMessage());
        assertEquals(FarmerPhase.RESTING, runtime.phase());
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
        assertNull(field(runtime, "hook"));
    }

    @Test
    void releasingWorkStateReleasesLeaseWhenRemovingHookFails() throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        FishHook hook = mock(FishHook.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(hook.isValid()).thenReturn(true);
        doThrow(new IllegalStateException("hook remove failed")).when(hook).remove();
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "hook", hook);
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::releaseWorkState);

        assertEquals("hook remove failed", failure.getMessage());
        assertEquals(FarmerPhase.INACTIVE, runtime.phase());
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
        assertNull(field(runtime, "hook"));
    }

    @Test
    void reachingQuotaRestoresHandStateWhenRemovingHookFails() throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        FishHook hook = mock(FishHook.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        Location fishingWater = mock(Location.class);
        Location standingTarget = mock(Location.class);
        LivingEntity living = mock(LivingEntity.class);
        EntityEquipment entityEquipment = mock(EntityEquipment.class);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(false);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getEntity()).thenReturn(living);
        when(living.getEquipment()).thenReturn(entityEquipment);
        when(hook.isValid()).thenReturn(true);
        doThrow(new IllegalStateException("hook remove failed")).when(hook).remove();
        when(npc.getOrAddTrait(Equipment.class))
                .thenThrow(new IllegalStateException("equipment restore failed"));
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "hook", hook);
        setField(runtime, "ownsHand", true);
        setField(runtime, "fishingWater", fishingWater);
        setField(runtime, "standingTarget", standingTarget);
        FisherRuntime.NavigationPause pause =
                (FisherRuntime.NavigationPause) field(runtime, "navigationPause");
        pause.pause();
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::stopForQuota);

        assertEquals("hook remove failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("equipment restore failed", failure.getSuppressed()[0].getMessage());
        verify(npc).getOrAddTrait(Equipment.class);
        verify(entityEquipment).setItemInMainHand(null);
        assertEquals(FarmerPhase.RESTING, runtime.phase());
        assertFalse((boolean) field(runtime, "ownsHand"));
        assertNull(field(runtime, "previousHand"));
        assertNull(field(runtime, "hook"));
        assertNull(field(runtime, "fishingWater"));
        assertNull(field(runtime, "standingTarget"));
        assertFalse(pause.paused());
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void timedOutArrivalContinuesCleanupWhenRemovingHookFails()
            throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        FishHook hook = mock(FishHook.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        World world = mock(World.class);
        Location standingTarget = new Location(world, 4.5, 64, 4.5);
        when(world.getName()).thenReturn("StillCliff");
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(false);
        when(hook.isValid()).thenReturn(true);
        doThrow(new IllegalStateException("hook remove failed")).when(hook).remove();
        when(npc.getOrAddTrait(Equipment.class))
                .thenThrow(new IllegalStateException("equipment restore failed"));
        when(config.navigationRetryBackoffTicks()).thenReturn(30L);
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "phase", FarmerPhase.GOING_TO_FISHING_SPOT);
        setField(runtime, "standingTarget", standingTarget);
        setField(runtime, "hook", hook);
        setField(runtime, "ownsHand", true);
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        InvocationTargetException failure = assertThrows(
                InvocationTargetException.class,
                () -> invokeCheckArrival(runtime, 200L, config));

        assertEquals("hook remove failed", failure.getCause().getMessage());
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertEquals("equipment restore failed", failure.getCause().getSuppressed()[0].getMessage());
        verify(npc).getOrAddTrait(Equipment.class);
        assertEquals(FarmerPhase.RESTING, runtime.phase());
        assertEquals(230L, field(runtime, "nextActionTick"));
        assertFalse((boolean) field(runtime, "ownsHand"));
        assertNull(field(runtime, "hook"));
        assertNull(field(runtime, "standingTarget"));
        assertNull(field(runtime, "fishingWater"));
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void suspendingReleasesLeaseWhenCancelNavigationFails() {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        doThrow(new IllegalStateException("cancel failed")).when(navigator).cancelNavigation();
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::suspend);

        assertEquals("cancel failed", failure.getMessage());
        assertEquals(FarmerPhase.INACTIVE, runtime.phase());
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void teardownKeepsThePrimaryFailureWhenMultipleCleanupsThrowTheSameInstance()
            throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        FishHook hook = mock(FishHook.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        IllegalStateException sharedFailure = new IllegalStateException("shared cleanup failed");
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        doThrow(sharedFailure).when(navigator).cancelNavigation();
        when(hook.isValid()).thenReturn(true);
        doThrow(sharedFailure).when(hook).remove();
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "hook", hook);
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::suspend);

        assertTrue(failure == sharedFailure);
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(FarmerPhase.INACTIVE, runtime.phase());
        assertNull(field(runtime, "hook"));
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void reachingQuotaClearsHandOwnershipWhenRestoringEquipmentFails()
            throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(npc.getOrAddTrait(Equipment.class))
                .thenThrow(new IllegalStateException("equipment restore failed"));
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "ownsHand", true);
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::stopForQuota);

        assertEquals("equipment restore failed", failure.getMessage());
        assertEquals(FarmerPhase.RESTING, runtime.phase());
        assertFalse((boolean) field(runtime, "ownsHand"));
        assertNull(field(runtime, "previousHand"));
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void failedHandRestoreRetainsSnapshotForRetry() throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        LivingEntity living = mock(LivingEntity.class);
        EntityEquipment entityEquipment = mock(EntityEquipment.class);
        ItemStack previous = mock(ItemStack.class);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getOrAddTrait(Equipment.class))
                .thenThrow(new IllegalStateException("trait restore failed"));
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getEntity()).thenReturn(living);
        when(living.getEquipment()).thenReturn(entityEquipment);
        FisherRuntime runtime = new FisherRuntime(
                npc, mock(FarmerDefinition.class), mock(NpcEconomy.class), mock(VillageStore.class),
                new NavigationLeaseManager(), ignored -> { });
        setField(runtime, "previousHand", previous);
        setField(runtime, "ownsHand", true);

        assertThrows(IllegalStateException.class, runtime::releaseWorkState);
        assertTrue((boolean) field(runtime, "ownsHand"));
        assertSame(previous, field(runtime, "previousHand"));

        assertThrows(IllegalStateException.class, runtime::releaseWorkState);
        verify(npc, times(2)).getOrAddTrait(Equipment.class);
        verify(entityEquipment, times(2)).setItemInMainHand(previous);
        assertTrue((boolean) field(runtime, "ownsHand"));
        assertSame(previous, field(runtime, "previousHand"));
    }

    @Test
    void reachingQuotaDoesNotCancelNavigatorOwnedByHigherPriorityAuthority() {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));
        assertTrue(leases.claim(npcUuid, "door-passage", 90, null));

        runtime.stopForQuota();

        assertEquals(FarmerPhase.RESTING, runtime.phase());
        verify(navigator, never()).cancelNavigation();
        assertTrue(leases.heldBy(npcUuid, "door-passage"));
    }

    @Test
    void releasingForSleepCancelsAnOrphanedFisherNavigationBeforeReleasingItsLease() {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        runtime.releaseForSleep();

        verify(navigator).cancelNavigation();
        assertEquals(FarmerPhase.INACTIVE, runtime.phase());
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void releasingForSleepDoesNotCancelNavigationAfterSleepOwnerPreemptedFisher() {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));
        assertTrue(leases.claim(npcUuid, "sleep", 80, null));

        runtime.releaseForSleep();

        verify(navigator, never()).cancelNavigation();
        assertTrue(leases.heldBy(npcUuid, "sleep"));
    }

    @Test
    void newTargetIsInstalledBeforeConfiguringItsCitizensLocalParameters() {
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters active = new NavigatorParameters();
        Location target = mock(Location.class);
        when(navigator.getLocalParameters()).thenReturn(active);

        FisherRuntime.startNavigation(navigator, target, 0.85F);

        var ordered = inOrder(navigator);
        ordered.verify(navigator).setTarget(target);
        ordered.verify(navigator).getLocalParameters();
        assertEquals(0.85F, active.speedModifier());
        assertEquals(1.25, active.distanceMargin());
        assertEquals(1.25, active.pathDistanceMargin());
        assertEquals(0.0, active.destinationTeleportMargin());
        assertTrue(active.hasExaminer(LivingDoorExaminer.class));
    }

    @Test
    void failedTripStartupCancelsNavigationAndRollsBackLeaseAndTargets()
            throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        Entity entity = mock(Entity.class);
        World world = mock(World.class);
        Block water = mock(Block.class);
        Block aboveWater = mock(Block.class);
        Block blocked = mock(Block.class);
        Block standing = mock(Block.class);
        Levelled waterData = mock(Levelled.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(npc.getEntity()).thenReturn(entity);
        when(entity.getLocation()).thenReturn(new Location(world, 2.5, 64, 0.5));
        when(world.getName()).thenReturn("StillCliff");
        when(world.getBlockAt(0, 64, 0)).thenReturn(water);
        when(water.getType()).thenReturn(Material.WATER);
        when(water.getBlockData()).thenReturn(waterData);
        when(waterData.getLevel()).thenReturn(0);
        when(water.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(water.getRelative(anyInt(), anyInt(), anyInt())).thenReturn(blocked);
        when(blocked.getRelative(0, -1, 0)).thenReturn(mock(Block.class));
        when(blocked.isPassable()).thenReturn(false);
        when(aboveWater.isPassable()).thenReturn(true);
        when(water.getRelative(0, 1, 0)).thenReturn(aboveWater);
        when(water.getRelative(2, 0, 0)).thenReturn(standing);
        when(standing.getLocation()).thenReturn(new Location(world, 2, 64, 0));
        makeSafeStanding(standing);
        for (BlockFace face : new BlockFace[] {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        }) {
            Block adjacent = mock(Block.class);
            when(standing.getRelative(face)).thenReturn(adjacent);
            makeSafeStanding(adjacent);
        }
        when(config.fisher()).thenReturn(new FisherSettings(20L, 20L, 1.0, 1, 0, 0));
        when(config.navigationSpeedModifier()).thenReturn(0.85F);
        when(navigator.isNavigating()).thenReturn(true);
        when(navigator.getLocalParameters()).thenThrow(new IllegalStateException("parameters failed"));
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "phase", FarmerPhase.RESTING);

        InvocationTargetException failure = assertThrows(
                InvocationTargetException.class,
                () -> invokeStartTrip(runtime, new Location(world, 0, 64, 0), 100L, config));

        assertEquals(IllegalStateException.class, failure.getCause().getClass());
        assertEquals("parameters failed", failure.getCause().getMessage());
        verify(navigator).cancelNavigation();
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
        assertEquals(FarmerPhase.RESTING, runtime.phase());
        assertNull(field(runtime, "standingTarget"));
        assertNull(field(runtime, "fishingWater"));
    }

    @Test
    void failedTripCancelsActiveNavigatorBeforeReleasingItsLease() {
        UUID npcUuid = UUID.randomUUID();
        NavigationLeaseManager leases = new NavigationLeaseManager();
        Navigator navigator = mock(Navigator.class);
        when(navigator.isNavigating()).thenReturn(true);
        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));

        FisherRuntime.cancelAndReleaseNavigation(leases, npcUuid, navigator);

        verify(navigator).cancelNavigation();
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void failedResumeNavigationCancelsTemporaryTargetAndReleasesReclaimedLease()
            throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        NavigationLeaseManager leases = new NavigationLeaseManager();
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        World world = mock(World.class);
        Location standingTarget = new Location(world, 4.5, 64, 4.5);

        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        when(navigator.getLocalParameters())
                .thenThrow(new IllegalStateException("resume parameters failed"));
        when(config.navigationSpeedModifier()).thenReturn(0.85F);

        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "phase", FarmerPhase.GOING_TO_FISHING_SPOT);
        setField(runtime, "standingTarget", standingTarget);
        FisherRuntime.NavigationPause pause = (FisherRuntime.NavigationPause) field(runtime, "navigationPause");
        pause.pause();

        InvocationTargetException failure = assertThrows(
                InvocationTargetException.class,
                () -> invokeCheckArrival(runtime, 200L, config));

        assertEquals("resume parameters failed", failure.getCause().getMessage());
        verify(navigator).cancelNavigation();
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    @Test
    void startTripWithNoTargetCancelsTemporaryNavigationBeforeRelease()
            throws ReflectiveOperationException {
        UUID npcUuid = UUID.randomUUID();
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        World world = mock(World.class);
        Block nonWater = mock(Block.class);
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        when(npc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(true);
        when(npc.getEntity()).thenReturn(mock(Entity.class));
        when(world.getName()).thenReturn("StillCliff");
        doReturn(nonWater).when(world).getBlockAt(anyInt(), anyInt(), anyInt());
        when(nonWater.getType()).thenReturn(Material.STONE);
        when(config.fisher()).thenReturn(new FisherSettings(20L, 20L, 1.0, 1, 0, 0));
        NavigationLeaseManager leases = new NavigationLeaseManager();
        FisherRuntime runtime = new FisherRuntime(
                npc,
                mock(FarmerDefinition.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class),
                leases,
                ignored -> { });
        setField(runtime, "phase", FarmerPhase.RESTING);

        assertTrue(FisherRuntime.claimNavigation(leases, npcUuid, null));
        invokeStartTrip(runtime, new Location(world, 0, 64, 0), 100L, config);

        assertEquals(FarmerPhase.RESTING, runtime.phase());
        verify(navigator).cancelNavigation();
        assertFalse(leases.heldBy(npcUuid, FisherRuntime.NAVIGATION_OWNER));
    }

    private static Object field(FisherRuntime runtime, String name) throws ReflectiveOperationException {
        Field field = FisherRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(runtime);
    }

    private static void setField(FisherRuntime runtime, String name, Object value)
            throws ReflectiveOperationException {
        Field field = FisherRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(runtime, value);
    }

    private static void invokeStartTrip(
            FisherRuntime runtime, Location center, long serverTick, LivingNpcConfig config)
            throws ReflectiveOperationException {
        Method method = FisherRuntime.class.getDeclaredMethod(
                "startTrip", Location.class, long.class, LivingNpcConfig.class);
        method.setAccessible(true);
        method.invoke(runtime, center, serverTick, config);
    }

    private static void invokeCheckArrival(
            FisherRuntime runtime, long serverTick, LivingNpcConfig config)
            throws ReflectiveOperationException {
        Method method = FisherRuntime.class.getDeclaredMethod(
                "checkArrival", long.class, LivingNpcConfig.class);
        method.setAccessible(true);
        method.invoke(runtime, serverTick, config);
    }

    private static boolean invokeCastLine(FisherRuntime runtime, long serverTick)
            throws ReflectiveOperationException {
        Method method = FisherRuntime.class.getDeclaredMethod("castLine", long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(runtime, serverTick);
    }

    private static void makeSafeStanding(Block block) {
        Block support = mock(Block.class);
        Block head = mock(Block.class);
        Material supportType = mock(Material.class);
        when(block.isPassable()).thenReturn(true);
        when(block.isLiquid()).thenReturn(false);
        when(block.getRelative(0, -1, 0)).thenReturn(support);
        when(block.getRelative(0, 1, 0)).thenReturn(head);
        when(support.getType()).thenReturn(supportType);
        when(supportType.isSolid()).thenReturn(true);
        when(head.isPassable()).thenReturn(true);
        when(head.isLiquid()).thenReturn(false);
    }
}