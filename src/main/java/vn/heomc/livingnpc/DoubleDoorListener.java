package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.event.NPCOpenDoorEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

final class DoubleDoorListener implements Listener {
    static final String NAVIGATION_OWNER = "door-passage";
    private static final int NAVIGATION_PRIORITY = 90;
    private static final double OPEN_DISTANCE_SQUARED = 2.75 * 2.75;
    private static final double CENTRE_MARGIN_SQUARED = 0.3 * 0.3;
    private static final double CENTRE_VERTICAL_MARGIN = 0.75;
    private static final int WAIT_BEFORE_OPEN_TICKS = 40;
    private static final int WAIT_AFTER_OPEN_TICKS = 40;
    private static final int PASSAGE_TIMEOUT_TICKS = 240;

    private final LivingNpcPlugin plugin;
    private final NavigationLeaseManager navigationLeases;
    private final Map<UUID, DoorPassage> activePassages = new HashMap<>();
    private final Map<UUID, DoorTrace> lastTrace = new HashMap<>();
    private final Set<BlockKey> authorizing = new java.util.HashSet<>();
    private final OwnedTaskRegistry passageTasks = new OwnedTaskRegistry();
    private final DoorPassageCoordinator passageCoordinator = new DoorPassageCoordinator();
    private boolean accepting = true;

    DoubleDoorListener(LivingNpcPlugin plugin) {
        this.plugin = plugin;
        this.navigationLeases = plugin.manager().navigationLeases();
    }

    /**
     * Chốt listener rồi tháo mọi passage đang chạy.
     *
     * <p>{@link #onNpcOpenDoor} vẫn được đăng ký sau khi runtime dừng, nên stop phải chặn
     * passage mới thay vì chỉ dọn passage cũ; nếu không một event cửa đến sau sẽ tạo lại
     * lease, task lặp và mutation block mà coordinator không còn dọn nữa.</p>
     */
    void shutdown() {
        accepting = false;
        RuntimeException failure = null;
        try {
            passageTasks.cancelAll();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        for (Map.Entry<UUID, DoorPassage> entry : List.copyOf(activePassages.entrySet())) {
            try {
                NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(entry.getKey());
                releasePassageAfter(navigationLeases, entry.getKey(), () -> {
                    if (npc == null) closeDoors(entry.getValue());
                    else terminatePassageCleanup(npc, entry.getValue(), "ABORTED_SHUTDOWN", false);
                });
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        activePassages.clear();
        passageCoordinator.shutdown();
        if (failure != null) throw failure;
    }

    /** Mở lại listener khi runtime được bật lại. Idempotent. */
    void resume() {
        accepting = true;
    }

    DoorPassageCoordinator passageCoordinator() {
        return passageCoordinator;
    }

    void recoverObstructedManagedNpcs() {
        if (plugin.manager() == null) return;
        for (FarmerDefinition definition : plugin.manager().definitions()) {
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            if (npc == null || !npc.isSpawned() || !npc.getNavigator().isNavigating()
                    || activePassages.containsKey(npc.getUniqueId())) continue;
            Optional<Block> obstructing = findObstructingDoor(npc.getStoredLocation());
            if (obstructing.isEmpty()) continue;
            Block source = DoubleDoorSupport.bottom(obstructing.orElseThrow());
            Block partner = DoubleDoorSupport.findPartner(source);
            if (partner != null && closestDoorLeaf(
                    npc.getStoredLocation(), source.getLocation(), partner.getLocation()).equals(partner.getLocation())) {
                source = partner;
            }
            if (!(source.getBlockData() instanceof Door door)) continue;
            DoorSides sides = doorSides(npc.getStoredLocation(), source.getLocation(), door.getFacing());
            if (sides == null || !safeStanding(sides.before()) || !safeStanding(sides.after())) {
                traceDoor(npc, source, "RECOVERY_BLOCKED_UNSAFE_SIDES");
                continue;
            }
            traceDoor(npc, source, "RECOVERY_INTERSECTING_CLOSED_DOOR");
            startPassage(npc, source, sides, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcOpenDoor(NPCOpenDoorEvent event) {
        NPC npc = event.getNPC();
        if (plugin.manager() == null || !plugin.manager().manages(npc.getUniqueId())) return;
        Block source = DoubleDoorSupport.bottom(event.getDoor());
        if (authorizing.contains(BlockKey.of(source))) return;
        Block partner = DoubleDoorSupport.findPartner(source);
        if (partner != null && closestDoorLeaf(
                npc.getStoredLocation(), source.getLocation(), partner.getLocation()).equals(partner.getLocation())) {
            source = partner;
        }
        event.setCancelled(true);
        if (activePassages.containsKey(npc.getUniqueId())) return;
        if (!withinOpeningRange(npc.getStoredLocation(), source.getLocation())) {
            traceDoor(npc, source, "BLOCKED_TOO_FAR");
            return;
        }
        if (!(source.getBlockData() instanceof Door door)) return;

        DoorSides sides = doorSides(npc.getStoredLocation(), source.getLocation(), door.getFacing());
        if (sides == null || !safeStanding(sides.before()) || !safeStanding(sides.after())) {
            traceDoor(npc, source, "BLOCKED_UNSAFE_SIDES");
            return;
        }
        startPassage(npc, source, sides, false);
    }

    void startPassage(NPC npc, Block source, DoorSides sides, boolean intersectingDoor) {
        if (!accepting) {
            traceDoor(npc, source, "BLOCKED_RUNTIME_STOPPED");
            return;
        }
        DoorPassageCoordinator.DoorKey doorKey = DoorPassageCoordinator.DoorKey.of(source);
        DoorPassageCoordinator.Result result = passageCoordinator.request(
                doorKey, npc.getUniqueId(), () -> startOwnedPassage(npc, source, sides, intersectingDoor, doorKey));
        if (result == DoorPassageCoordinator.Result.WAITING) {
            traceDoor(npc, source, "WAITING_DOOR_QUEUE");
            return;
        }
        if (result != DoorPassageCoordinator.Result.OWNER) {
            traceDoor(npc, source, result == DoorPassageCoordinator.Result.REJECTED
                    ? "BLOCKED_DOOR_QUEUE_FULL" : "DUPLICATE_DOOR_QUEUE");
            return;
        }
        startOwnedPassage(npc, source, sides, intersectingDoor, doorKey);
    }

    private void startOwnedPassage(NPC npc, Block source, DoorSides sides, boolean intersectingDoor,
            DoorPassageCoordinator.DoorKey doorKey) {
        Navigator navigator = npc.getNavigator();
        Location originalTarget = navigator.getTargetAsLocation();
        NavigatorParameters originalParameters = navigator.getLocalParameters().clone();
        if (!claimPassage(navigationLeases, npc.getUniqueId())) {
            passageCoordinator.release(doorKey, npc.getUniqueId(), "BLOCKED_NAVIGATION_OWNER");
            traceDoor(npc, source, "BLOCKED_NAVIGATION_OWNER");
            return;
        }
        DoorPassage passage = new DoorPassage(
                source, sides, originalTarget == null ? null : originalTarget.clone(), originalParameters, doorKey);
        activePassages.put(npc.getUniqueId(), passage);
        try {
            navigator.setTarget(passageStartTarget(sides, intersectingDoor));
            configurePassage(navigator.getLocalParameters(), originalParameters,
                    intersectingDoor ? 0.0F : originalParameters.speedModifier());
            if (intersectingDoor) {
                npc.getEntity().setVelocity(new org.bukkit.util.Vector());
                passage.phase = PassagePhase.WAITING_TO_OPEN;
                traceDoor(npc, source, "RECOVERY_WAITING_TO_OPEN");
            } else {
                traceDoor(npc, source, "APPROACH_WAIT");
            }

            passage.task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    tickPassage(npc, passage, navigator, originalParameters);
                } catch (RuntimeException failure) {
                    try {
                        terminatePassage(
                                passage, () -> abortPassage(npc, passage, "ABORTED_EXCEPTION"));
                    } catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                    plugin.getLogger().log(Level.WARNING,
                            "Door passage lỗi cho NPC " + npc.getUniqueId() + "; đã teardown fail-safe.",
                            failure);
                }
            }
            }.runTaskTimer(plugin, 1L, 1L);
            passageTasks.add(passage.task);
        } catch (RuntimeException failure) {
            rollbackPassageStartup(npc, passage, failure);
        }
    }

    private void tickPassage(
            NPC npc, DoorPassage passage, Navigator navigator,
            NavigatorParameters originalParameters) {
                DoorSides sides = passage.sides;
                Block source = passage.source;
                if (!npc.isSpawned() || !activePassages.containsKey(npc.getUniqueId())) {
                    terminatePassage(passage, () -> abortPassage(npc, passage, "ABORTED_DESPAWN"));
                    return;
                }
                if (!ownsPassage(navigationLeases, npc.getUniqueId())) {
                    terminatePassage(passage, () -> relinquishPassage(
                            npc, passage, "PREEMPTED_OWNER", PassageTargetState.PREEMPTED));
                    return;
                }
                passage.elapsedTicks++;
                if (passage.elapsedTicks >= PASSAGE_TIMEOUT_TICKS) {
                    terminatePassage(passage, () -> abortPassage(npc, passage, "ABORTED_TIMEOUT"));
                    return;
                }
                Location current = npc.getStoredLocation();
                if (current == null || !current.getWorld().equals(sides.before().getWorld())) {
                    terminatePassage(passage, () -> abortPassage(npc, passage, "ABORTED_WORLD"));
                    return;
                }
                Location expectedTarget = passage.phase == PassagePhase.APPROACHING
                        ? sides.before() : sides.after();
                PassageTargetState targetState = passageTargetState(
                        current, navigator.getTargetAsLocation(), expectedTarget);
                if (passage.phase == PassagePhase.APPROACHING
                        && targetState == PassageTargetState.REACHED) {
                    navigator.setTarget(sides.after());
                    configurePassage(navigator.getLocalParameters(), originalParameters, 0.0F);
                    npc.getEntity().setVelocity(new org.bukkit.util.Vector());
                    passage.phase = PassagePhase.WAITING_TO_OPEN;
                    passage.phaseTicks = 0;
                    traceDoor(npc, source, "CENTERED_WAITING_TO_OPEN");
                    return;
                }
                if (passage.phase == PassagePhase.CROSSING
                        && targetState == PassageTargetState.REACHED) {
                    terminatePassage(passage, () -> finishPassage(npc, passage));
                    return;
                }
                if (targetState == PassageTargetState.PREEMPTED
                        || targetState == PassageTargetState.TARGET_CLEARED) {
                    terminatePassage(passage, () -> relinquishPassage(
                            npc, passage, targetState.name(), targetState));
                    return;
                }
                if (passage.phase == PassagePhase.WAITING_TO_OPEN) {
                    npc.getEntity().setVelocity(new org.bukkit.util.Vector());
                    passage.phaseTicks++;
                    if (passage.phaseTicks < WAIT_BEFORE_OPEN_TICKS) return;
                    if (!openDoors(npc, passage)) {
                        terminatePassage(
                                passage, () -> abortPassage(npc, passage, "ABORTED_DOOR_CHANGED"));
                        return;
                    }
                    passage.phase = PassagePhase.WAITING_TO_CROSS;
                    passage.phaseTicks = 0;
                    traceDoor(npc, source, "OPEN_WAIT");
                    return;
                }
                if (passage.phase == PassagePhase.WAITING_TO_CROSS) {
                    npc.getEntity().setVelocity(new org.bukkit.util.Vector());
                    passage.phaseTicks++;
                    if (passage.phaseTicks < WAIT_AFTER_OPEN_TICKS) return;
                    passage.phase = PassagePhase.CROSSING;
                    configurePassage(
                            navigator.getLocalParameters(), originalParameters, originalParameters.speedModifier());
                    traceDoor(npc, source, "CROSSING");
                    return;
                }
    }

    boolean hasActivePassage(UUID npcUuid) {
        return activePassages.containsKey(npcUuid);
    }

    private void rollbackPassageStartup(NPC npc, DoorPassage passage, RuntimeException failure) {
        activePassages.remove(npc.getUniqueId(), passage);
        RuntimeException cleanupFailure = null;
        try {
            try {
                cancelPassageTask(passage);
            } catch (RuntimeException exception) {
                cleanupFailure = appendFailure(cleanupFailure, exception);
            }
            try {
                closeDoors(passage);
            } catch (RuntimeException exception) {
                cleanupFailure = appendFailure(cleanupFailure, exception);
            }
            try {
                if (navigationLeases.heldBy(npc.getUniqueId(), NAVIGATION_OWNER)) {
                    Navigator navigator = npc.getNavigator();
                    Location originalTarget = passage.originalTarget;
                    if (originalTarget == null) {
                        try {
                            navigator.cancelNavigation();
                        } catch (RuntimeException exception) {
                            cleanupFailure = appendFailure(cleanupFailure, exception);
                        }
                    } else {
                        boolean restoreFailed = false;
                        try {
                            navigator.setTarget(originalTarget);
                        } catch (RuntimeException exception) {
                            cleanupFailure = appendFailure(cleanupFailure, exception);
                            restoreFailed = true;
                        }
                        if (restoreFailed) {
                            try {
                                navigator.cancelNavigation();
                            } catch (RuntimeException exception) {
                                cleanupFailure = appendFailure(cleanupFailure, exception);
                            }
                        }
                    }
                    try {
                        restoreParameters(navigator.getLocalParameters(), passage.originalParameters);
                    } catch (RuntimeException exception) {
                        cleanupFailure = appendFailure(cleanupFailure, exception);
                    }
                }
            } catch (RuntimeException exception) {
                cleanupFailure = appendFailure(cleanupFailure, exception);
            }
        } finally {
            releasePassage(navigationLeases, npc.getUniqueId());
            passageCoordinator.release(passage.doorKey, npc.getUniqueId(), "ABORTED_START");
        }
        if (cleanupFailure != null && failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
        plugin.getLogger().log(Level.WARNING,
                "Không thể khởi tạo door passage cho NPC " + npc.getUniqueId()
                        + "; đã rollback lease và navigator state.",
                failure);
    }

    private void cancelPassageTask(DoorPassage passage) {
        if (passage.task == null) return;
        BukkitTask current = passage.task;
        passage.task = null;
        try {
            current.cancel();
        } finally {
            passageTasks.remove(current);
        }
    }

    private void terminatePassage(DoorPassage passage, Runnable cleanup) {
        try {
            cleanup.run();
        } finally {
            cancelPassageTask(passage);
        }
    }

    private boolean openDoors(NPC npc, DoorPassage passage) {
        List<Block> doors = new ArrayList<>();
        doors.add(passage.source);
        Block partner = DoubleDoorSupport.findPartner(passage.source);
        if (partner != null) doors.add(partner);
        for (Block block : doors) {
            if (!(block.getBlockData() instanceof Door)) return false;
            BlockKey key = BlockKey.of(block);
            authorizing.add(key);
            NPCOpenDoorEvent event = new NPCOpenDoorEvent(npc, block);
            try {
                Bukkit.getPluginManager().callEvent(event);
            } finally {
                authorizing.remove(key);
            }
            if (event.isCancelled()) return false;
        }
        for (Block block : doors) {
            Door door = (Door) block.getBlockData();
            if (door.isOpen()) continue;
            passage.openedDoors.add(new OpenedDoor(
                    block, block.getType(), block.getBlockData().getAsString()));
            door.setOpen(true);
            block.setBlockData(door);

        }
        return true;
    }

    private void finishPassage(NPC npc, DoorPassage passage) {
        activePassages.remove(npc.getUniqueId());
        releasePassageAfter(navigationLeases, npc.getUniqueId(),
                () -> terminatePassageCleanup(npc, passage, "RESUMED", true));
        passageCoordinator.release(passage.doorKey, npc.getUniqueId(), "RESUMED");
    }

    private void abortPassage(NPC npc, DoorPassage passage, String result) {
        activePassages.remove(npc.getUniqueId());
        releasePassageAfter(navigationLeases, npc.getUniqueId(),
                () -> terminatePassageCleanup(npc, passage, result, true));
        passageCoordinator.release(passage.doorKey, npc.getUniqueId(), result);
    }

    private void relinquishPassage(
            NPC npc, DoorPassage passage, String result, PassageTargetState targetState) {
        activePassages.remove(npc.getUniqueId());
        releasePassageAfter(navigationLeases, npc.getUniqueId(),
                () -> terminatePassageCleanup(npc, passage, result,
                        shouldRestoreOriginalTarget(targetState)));
        passageCoordinator.release(passage.doorKey, npc.getUniqueId(), result);
    }

    private void terminatePassageCleanup(
            NPC npc, DoorPassage passage, String result, boolean restoreOriginalTarget) {
        RuntimeException failure = null;
        try {
            closeDoors(passage);
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        if (ownsPassage(navigationLeases, npc.getUniqueId())) {
            Navigator navigator = null;
            try {
                navigator = npc.getNavigator();
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
            if (navigator != null) {
                if (restoreOriginalTarget && canRestoreOriginalTarget(npc, passage.originalTarget)) {
                    try {
                        navigator.setTarget(passage.originalTarget);
                    } catch (RuntimeException exception) {
                        failure = appendFailure(failure, exception);
                    }
                    try {
                        restoreParameters(navigator.getLocalParameters(), passage.originalParameters);
                    } catch (RuntimeException exception) {
                        failure = appendFailure(failure, exception);
                    }
                } else if (npc.isSpawned() && navigator.isNavigating()) {
                    try {
                        navigator.cancelNavigation();
                    } catch (RuntimeException exception) {
                        failure = appendFailure(failure, exception);
                    }
                }
            }
        }
        try {
            traceDoor(npc, passage.source, result);
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        if (failure != null) throw failure;
    }

    private static boolean canRestoreOriginalTarget(NPC npc, Location originalTarget) {
        if (originalTarget == null || !npc.isSpawned()) return false;
        Location current = npc.getStoredLocation();
        return current != null && current.getWorld() != null
                && current.getWorld().equals(originalTarget.getWorld());
    }

    private static RuntimeException appendFailure(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    static boolean claimPassage(NavigationLeaseManager leases, UUID npcUuid) {
        return leases.claim(npcUuid, NAVIGATION_OWNER, NAVIGATION_PRIORITY, null);
    }

    static void releasePassage(NavigationLeaseManager leases, UUID npcUuid) {
        leases.release(npcUuid, NAVIGATION_OWNER);
    }

    static void releasePassageAfter(
            NavigationLeaseManager leases, UUID npcUuid, Runnable cleanup) {
        try {
            cleanup.run();
        } finally {
            releasePassage(leases, npcUuid);
        }
    }

    static boolean ownsPassage(NavigationLeaseManager leases, UUID npcUuid) {
        return leases.heldBy(npcUuid, NAVIGATION_OWNER);
    }

    private void closeDoors(DoorPassage passage) {
        for (OpenedDoor opened : passage.openedDoors) {
            if (opened.block().getType() != opened.material()
                    || !(opened.block().getBlockData() instanceof Door door) || !door.isOpen()) continue;
            opened.block().setBlockData(Bukkit.createBlockData(opened.closedState()));

        }
        passage.openedDoors.clear();
    }

    private static void configurePassage(
            NavigatorParameters target, NavigatorParameters original, float speedModifier) {
        restoreParameters(target, original);
        LivingNavigation.allowDoors(target)
                .speedModifier(speedModifier)
                .distanceMargin(0.2)
                .pathDistanceMargin(0.2)
                .destinationTeleportMargin(0.0)
                .stationaryTicks(PASSAGE_TIMEOUT_TICKS)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
    }

    private static void restoreParameters(NavigatorParameters target, NavigatorParameters source) {
        target.clearExaminers();
        for (var examiner : source.examiners()) target.examiner(examiner);
        target.pathfinderType(source.pathfinderType())
                .avoidWater(source.avoidWater())
                .fallDistance(source.fallDistance())
                .speedModifier(source.speedModifier())
                .distanceMargin(source.distanceMargin())
                .pathDistanceMargin(source.pathDistanceMargin())
                .destinationTeleportMargin(source.destinationTeleportMargin())
                .range(source.range())
                .updatePathRate(source.updatePathRate())
                .stationaryTicks(source.stationaryTicks())
                .stuckAction(source.stuckAction());
    }

    private void traceDoor(NPC npc, Block door, String result) {
        long tick = Bukkit.getCurrentTick();
        DoorTrace trace = new DoorTrace(BlockKey.of(door), result, tick);
        DoorTrace previous = lastTrace.get(npc.getUniqueId());
        if (previous != null && previous.key().equals(trace.key()) && previous.result().equals(result)
                && tick - previous.tick() < 20L) return;
        lastTrace.put(npc.getUniqueId(), trace);
        Location location = npc.getStoredLocation();
        String position = location == null || location.getWorld() == null ? "none"
                : location.getWorld().getName() + ":" + location.getBlockX() + ","
                        + location.getBlockY() + "," + location.getBlockZ();
        plugin.getLogger().info("NPC_DOOR uuid=" + npc.getUniqueId() + " result=" + result
                + " npcPos=" + position + " door=" + trace.key().world() + ":"
                + trace.key().x() + "," + trace.key().y() + "," + trace.key().z());
    }

    static DoorSides doorSides(Location npcLocation, Location doorLocation, BlockFace facing) {
        if (npcLocation == null || doorLocation == null || facing == null
                || npcLocation.getWorld() == null || !npcLocation.getWorld().equals(doorLocation.getWorld())) {
            return null;
        }
        Location centre = doorLocation.clone().add(0.5, 0, 0.5);
        double dot = (npcLocation.getX() - centre.getX()) * facing.getModX()
                + (npcLocation.getZ() - centre.getZ()) * facing.getModZ();
        int side = dot >= 0.0 ? 1 : -1;
        Location before = centre.clone().add(facing.getModX() * side, 0, facing.getModZ() * side);
        Location after = centre.clone().add(-facing.getModX() * side, 0, -facing.getModZ() * side);
        return new DoorSides(before, after);
    }

    static Location closestDoorLeaf(Location npcLocation, Location first, Location second) {
        if (npcLocation == null || first == null || second == null
                || npcLocation.getWorld() == null || first.getWorld() == null || second.getWorld() == null
                || !npcLocation.getWorld().equals(first.getWorld())
                || !npcLocation.getWorld().equals(second.getWorld())) {
            return first;
        }
        Location firstCenter = first.clone().add(0.5, 0, 0.5);
        Location secondCenter = second.clone().add(0.5, 0, 0.5);
        return npcLocation.distanceSquared(secondCenter) < npcLocation.distanceSquared(firstCenter) ? second : first;
    }

    static boolean withinOpeningRange(Location npcLocation, Location doorLocation) {
        if (npcLocation == null || doorLocation == null
                || npcLocation.getWorld() == null || !npcLocation.getWorld().equals(doorLocation.getWorld())) {
            return false;
        }
        return npcLocation.distanceSquared(doorLocation.clone().add(0.5, 0, 0.5)) <= OPEN_DISTANCE_SQUARED;
    }

    static Optional<Block> findObstructingDoor(Location npcLocation) {
        if (npcLocation == null || npcLocation.getWorld() == null) return Optional.empty();
        int minX = (int) Math.floor(npcLocation.getX() - 0.3);
        int maxX = (int) Math.floor(npcLocation.getX() + 0.3 - 1.0E-9);
        int minY = (int) Math.floor(npcLocation.getY());
        int maxY = (int) Math.floor(npcLocation.getY() + 1.8 - 1.0E-9);
        int minZ = (int) Math.floor(npcLocation.getZ() - 0.3);
        int maxZ = (int) Math.floor(npcLocation.getZ() + 0.3 - 1.0E-9);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = npcLocation.getWorld().getBlockAt(x, y, z);
                    if (block.getBlockData() instanceof Door door && !door.isOpen()) return Optional.of(block);
                }
            }
        }
        return Optional.empty();
    }

    static Location passageStartTarget(DoorSides sides, boolean intersectingDoor) {
        return intersectingDoor ? sides.after() : sides.before();
    }

    static Location preemptionRecoveryTarget(Location originalTarget) {
        return originalTarget == null ? null : originalTarget.clone();
    }

    static boolean passageTargetReached(Location current, Location target) {
        if (current == null || target == null || current.getWorld() == null
                || !current.getWorld().equals(target.getWorld())) return false;
        return withinPassageMargin(current, target.getX(), target.getY(), target.getZ())
                || withinPassageMargin(
                        current, target.getBlockX(), target.getBlockY(), target.getBlockZ());
    }

    private static boolean withinPassageMargin(Location current, double x, double y, double z) {
        double deltaX = current.getX() - x;
        double deltaZ = current.getZ() - z;
        return deltaX * deltaX + deltaZ * deltaZ <= CENTRE_MARGIN_SQUARED
                && Math.abs(current.getY() - y) <= CENTRE_VERTICAL_MARGIN;
    }

    static PassageTargetState passageTargetState(Location current, Location actualTarget, Location expectedTarget) {
        if (actualTarget != null && !sameTarget(actualTarget, expectedTarget)) {
            return PassageTargetState.PREEMPTED;
        }
        if (passageTargetReached(current, expectedTarget)) return PassageTargetState.REACHED;
        if (sameTarget(actualTarget, expectedTarget)) return PassageTargetState.OWNED;
        return PassageTargetState.TARGET_CLEARED;
    }

    static boolean shouldRestoreOriginalTarget(PassageTargetState state) {
        return state == PassageTargetState.TARGET_CLEARED;
    }

    private static boolean sameTarget(Location actual, Location expected) {
        return actual != null && expected != null && actual.getWorld() != null
                && actual.getWorld().equals(expected.getWorld())
                && actual.distanceSquared(expected) < 0.01;
    }

    private static boolean safeStanding(Location location) {
        Block feet = location.getBlock();
        return feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable()
                && feet.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    record DoorSides(Location before, Location after) {
    }

    private enum PassagePhase {
        APPROACHING,
        WAITING_TO_OPEN,
        WAITING_TO_CROSS,
        CROSSING
    }

    enum PassageTargetState {
        REACHED,
        OWNED,
        TARGET_CLEARED,
        PREEMPTED
    }

    private static final class DoorPassage {
        private final Block source;
        private final DoorSides sides;
        private final Location originalTarget;
        private final NavigatorParameters originalParameters;
        private final DoorPassageCoordinator.DoorKey doorKey;
        private final List<OpenedDoor> openedDoors = new ArrayList<>();
        private BukkitTask task;
        private PassagePhase phase = PassagePhase.APPROACHING;
        private int elapsedTicks;
        private int phaseTicks;

        private DoorPassage(
                Block source, DoorSides sides, Location originalTarget, NavigatorParameters originalParameters) {
            this(source, sides, originalTarget, originalParameters, DoorPassageCoordinator.DoorKey.of(source));
        }

        private DoorPassage(
                Block source, DoorSides sides, Location originalTarget, NavigatorParameters originalParameters,
                DoorPassageCoordinator.DoorKey doorKey) {
            this.source = source;
            this.sides = sides;
            this.originalTarget = originalTarget;
            this.originalParameters = originalParameters;
            this.doorKey = doorKey;
        }
    }

    private record OpenedDoor(Block block, Material material, String closedState) {
    }

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }

    private record DoorTrace(BlockKey key, String result, long tick) {
    }
}
