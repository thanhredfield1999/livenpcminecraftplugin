package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

final class DoubleDoorListener implements Listener {
    private static final double OPEN_DISTANCE_SQUARED = 2.75 * 2.75;
    private static final double CENTRE_MARGIN_SQUARED = 0.3 * 0.3;
    private static final int WAIT_BEFORE_OPEN_TICKS = 40;
    private static final int WAIT_AFTER_OPEN_TICKS = 40;
    private static final int PASSAGE_TIMEOUT_TICKS = 240;

    private final LivingNpcPlugin plugin;
    private final Map<UUID, DoorPassage> activePassages = new HashMap<>();
    private final Map<UUID, DoorTrace> lastTrace = new HashMap<>();
    private final Set<BlockKey> authorizing = new java.util.HashSet<>();

    DoubleDoorListener(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    void shutdown() {
        for (DoorPassage passage : List.copyOf(activePassages.values())) closeDoors(passage);
        activePassages.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcOpenDoor(NPCOpenDoorEvent event) {
        NPC npc = event.getNPC();
        if (plugin.manager() == null || !plugin.manager().manages(npc.getUniqueId())) return;
        Block source = DoubleDoorSupport.bottom(event.getDoor());
        if (authorizing.contains(BlockKey.of(source))) return;
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
        startPassage(npc, source, sides);
    }

    private void startPassage(NPC npc, Block source, DoorSides sides) {
        Navigator navigator = npc.getNavigator();
        Location originalTarget = navigator.getTargetAsLocation();
        NavigatorParameters originalParameters = navigator.getLocalParameters().clone();
        DoorPassage passage = new DoorPassage(
                source, sides, originalTarget == null ? null : originalTarget.clone(), originalParameters);
        activePassages.put(npc.getUniqueId(), passage);
        navigator.setTarget(sides.before());
        configurePassage(navigator.getLocalParameters(), originalParameters, originalParameters.speedModifier());
        traceDoor(npc, source, "APPROACH_WAIT");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!npc.isSpawned() || !activePassages.containsKey(npc.getUniqueId())) {
                    abortPassage(npc, passage, "ABORTED_DESPAWN");
                    cancel();
                    return;
                }
                passage.elapsedTicks++;
                if (passage.elapsedTicks >= PASSAGE_TIMEOUT_TICKS) {
                    abortPassage(npc, passage, "ABORTED_TIMEOUT");
                    cancel();
                    return;
                }
                Location current = npc.getStoredLocation();
                if (current == null || !current.getWorld().equals(sides.before().getWorld())) {
                    abortPassage(npc, passage, "ABORTED_WORLD");
                    cancel();
                    return;
                }
                Location expectedTarget = passage.phase == PassagePhase.APPROACHING
                        ? sides.before() : sides.after();
                if (!sameTarget(navigator.getTargetAsLocation(), expectedTarget)) {
                    relinquishPassage(npc, passage, "PREEMPTED");
                    cancel();
                    return;
                }
                if (passage.phase == PassagePhase.APPROACHING
                        && current.distanceSquared(sides.before()) <= CENTRE_MARGIN_SQUARED) {
                    navigator.setTarget(sides.after());
                    configurePassage(navigator.getLocalParameters(), originalParameters, 0.0F);
                    npc.getEntity().setVelocity(new org.bukkit.util.Vector());
                    passage.phase = PassagePhase.WAITING_TO_OPEN;
                    passage.phaseTicks = 0;
                    traceDoor(npc, source, "CENTERED_WAITING_TO_OPEN");
                    return;
                }
                if (passage.phase == PassagePhase.WAITING_TO_OPEN) {
                    npc.getEntity().setVelocity(new org.bukkit.util.Vector());
                    passage.phaseTicks++;
                    if (passage.phaseTicks < WAIT_BEFORE_OPEN_TICKS) return;
                    if (!openDoors(npc, passage)) {
                        abortPassage(npc, passage, "ABORTED_DOOR_CHANGED");
                        cancel();
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
                if (passage.phase == PassagePhase.CROSSING
                        && current.distanceSquared(sides.after()) <= CENTRE_MARGIN_SQUARED) {
                    finishPassage(npc, passage);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
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
        Navigator navigator = npc.getNavigator();
        closeDoors(passage);
        if (passage.originalTarget != null
                && passage.originalTarget.getWorld().equals(npc.getStoredLocation().getWorld())
                && passage.originalTarget.distanceSquared(passage.sides.after()) > CENTRE_MARGIN_SQUARED) {
            navigator.setTarget(passage.originalTarget);
            restoreParameters(navigator.getLocalParameters(), passage.originalParameters);
        }
        traceDoor(npc, passage.source, "RESUMED");
    }

    private void abortPassage(NPC npc, DoorPassage passage, String result) {
        activePassages.remove(npc.getUniqueId());
        closeDoors(passage);
        if (npc.isSpawned() && passage.originalTarget != null) {
            npc.getNavigator().setTarget(passage.originalTarget);
            restoreParameters(npc.getNavigator().getLocalParameters(), passage.originalParameters);
        }
        traceDoor(npc, passage.source, result);
    }

    private void relinquishPassage(NPC npc, DoorPassage passage, String result) {
        activePassages.remove(npc.getUniqueId());
        closeDoors(passage);
        traceDoor(npc, passage.source, result);
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

    static boolean withinOpeningRange(Location npcLocation, Location doorLocation) {
        if (npcLocation == null || doorLocation == null
                || npcLocation.getWorld() == null || !npcLocation.getWorld().equals(doorLocation.getWorld())) {
            return false;
        }
        return npcLocation.distanceSquared(doorLocation.clone().add(0.5, 0, 0.5)) <= OPEN_DISTANCE_SQUARED;
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

    private static final class DoorPassage {
        private final Block source;
        private final DoorSides sides;
        private final Location originalTarget;
        private final NavigatorParameters originalParameters;
        private final List<OpenedDoor> openedDoors = new ArrayList<>();
        private PassagePhase phase = PassagePhase.APPROACHING;
        private int elapsedTicks;
        private int phaseTicks;

        private DoorPassage(
                Block source, DoorSides sides, Location originalTarget, NavigatorParameters originalParameters) {
            this.source = source;
            this.sides = sides;
            this.originalTarget = originalTarget;
            this.originalParameters = originalParameters;
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
