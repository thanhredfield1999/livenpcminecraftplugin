package vn.heomc.livingnpc;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class FisherRuntime {
    private static final double PRECISE_APPROACH_MARGIN = 1.25;
    static final String NAVIGATION_OWNER = "fisher-role";
    private static final int NAVIGATION_PRIORITY = 30;

    private final NPC npc;
    private final NpcEconomy economy;
    private final VillageStore villages;
    private final NavigationLeaseManager navigationLeases;
    private final java.util.function.LongConsumer experienceAwarder;
    private FarmerDefinition definition;
    private FarmerPhase phase = FarmerPhase.INACTIVE;
    private Location fishingWater;
    private Location standingTarget;
    private long navigationStartedTick;
    private long nextActionTick;
    private long shiftKey = Long.MIN_VALUE;
    private ItemStack previousHand;
    private boolean ownsHand;
    private FishHook hook;
    private long castLandingDeadline;
    private boolean castLanded;
    private long castStartedTick;
    private final java.util.Map<String, Long> nextHookTelemetryTick = new java.util.HashMap<>();
    private final java.util.Set<String> failedStandingTargets = new java.util.HashSet<>();
    private final NavigationPause navigationPause = new NavigationPause();

    FisherRuntime(NPC npc, FarmerDefinition definition, NpcEconomy economy, VillageStore villages,
                  NavigationLeaseManager navigationLeases,
                  java.util.function.LongConsumer experienceAwarder) {
        this.npc = npc;
        this.definition = definition;
        this.economy = economy;
        this.villages = villages;
        this.navigationLeases = navigationLeases;
        this.experienceAwarder = experienceAwarder;
    }

    void updateDefinition(FarmerDefinition updated) {
        definition = updated;
        if (updated.activeRole() != ResidentRole.FISHER && phase != FarmerPhase.INACTIVE) suspend();
    }

    FarmerPhase phase() {
        return phase;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        if (!npc.isSpawned()) {
            releaseWorkState();
            return;
        }
        if (definition.activeRole() != ResidentRole.FISHER || !definition.enabled(BehaviorFlag.MASTER)) {
            suspend();
            return;
        }
        VillageDefinition village = villages.get(definition.villageId());
        StoredLocation stored = village == null ? null : village.workZone(VillageWorkZoneType.FISHING);
        Location center = stored == null ? null : stored.resolve();
        if (center == null || !npc.getEntity().getWorld().equals(center.getWorld())
                || !RuntimeChunkAvailability.loaded(npc.getEntity().getLocation())
                || !RuntimeChunkAvailability.loadedArea(center, config.fisher().waterSearchRadius() + 3)) {
            suspend();
            return;
        }
        ResidentSchedule schedule = definition.schedule(
                ResidentRole.FISHER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        if (center.getWorld().hasStorm() || definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isScheduledTime(center.getWorld().getTime(), schedule)) {
            suspend();
            return;
        }
        long activeShift = SchedulePolicy.activeShiftKey(center.getWorld().getFullTime(), schedule);
        if (shiftKey != activeShift) {
            shiftKey = activeShift;
            if (nextActionTick == Long.MAX_VALUE) {
                nextActionTick = serverTick;
                phase = FarmerPhase.RESTING;
            }
            economy.canAcceptRoleProduction(
                    npc.getUniqueId(), village.id(), ResidentRole.FISHER,
                    1, config.fisher().maxCatchPerShift(), shiftKey);
        }
        if (!economy.canAcceptRoleProduction(
                npc.getUniqueId(), village.id(), ResidentRole.FISHER,
                1, config.fisher().maxCatchPerShift(), shiftKey)) {
            stopForQuota();
            return;
        }
        if (requiresFishingRod(phase)) holdRod();
        switch (phase) {
            case INACTIVE -> startTrip(center, serverTick, config);
            case RESTING -> {
                if (serverTick >= nextActionTick) startTrip(center, serverTick, config);
            }
            case GOING_TO_FISHING_SPOT -> checkArrival(serverTick, config);
            case CASTING_LINE -> {
                if (serverTick >= nextActionTick) {
                    faceWater();
                    if (castLine(serverTick)) {
                        phase = FarmerPhase.WAITING_FOR_BITE;
                        nextActionTick = serverTick + randomBetween(
                                config.fisher().attemptDelayMinTicks(), config.fisher().attemptDelayMaxTicks());
                    } else {
                        clearHand();
                        phase = FarmerPhase.RESTING;
                        nextActionTick = serverTick + 100L;
                    }
                }
            }
            case WAITING_FOR_BITE -> {
                faceWater();
                if (hook == null || !hook.isValid()) {
                    logHookFailure("HOOK_INVALID", serverTick);
                    phase = FarmerPhase.CASTING_LINE;
                    nextActionTick = serverTick + 20L;
                    break;
                }
                if (!castLanded) {
                    castLanded = hookInSelectedWater();
                    if (!castLanded && serverTick >= castLandingDeadline) {
                        logHookFailure("LANDING_TIMEOUT", serverTick);
                        clearHook();
                        phase = FarmerPhase.CASTING_LINE;
                        nextActionTick = serverTick + 20L;
                        break;
                    }
                }
                if (serverTick >= nextActionTick) {
                    phase = FarmerPhase.REELING_IN;
                    nextActionTick = serverTick + 20L;
                    clearHook();
                    if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
                }
            }
            case REELING_IN -> {
                if (serverTick >= nextActionTick) finishAttempt(serverTick, config, village.id());
            }
            default -> suspend();
        }
        if (requiresFishingRod(phase)) holdRod();
    }

    void suspend() {
        teardownWorkState(true, FarmerPhase.INACTIVE, null);
    }

    void releaseWorkState() {
        teardownWorkState(false, FarmerPhase.INACTIVE, null);
    }

    void releaseForSleep() {
        teardownWorkState(true, FarmerPhase.INACTIVE, null);
    }

    void stopForQuota() {
        teardownWorkState(true, FarmerPhase.RESTING, Long.MAX_VALUE);
    }

    private void teardownWorkState(
            boolean cancelNavigator, FarmerPhase targetPhase, Long targetNextActionTick) {
        teardownWorkState(cancelNavigator, targetPhase, targetNextActionTick, true);
    }

    private void teardownWorkState(
            boolean cancelNavigator, FarmerPhase targetPhase, Long targetNextActionTick,
            boolean clearFailedStandingTargets) {
        boolean terminalCleanup = cancelNavigator || ownsNavigation();
        RuntimeException failure = null;
        try {
            if (cancelNavigator && ownsNavigation() && npc.getNavigator().isNavigating()) {
                npc.getNavigator().cancelNavigation();
            }
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            clearHook(terminalCleanup);
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        try {
            clearHand(terminalCleanup);
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        phase = targetPhase;
        if (targetNextActionTick != null) this.nextActionTick = targetNextActionTick;
        fishingWater = null;
        standingTarget = null;
        if (clearFailedStandingTargets) failedStandingTargets.clear();
        navigationPause.resume();
        try {
            releaseNavigation();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException appendFailure(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private void startTrip(Location center, long serverTick, LivingNpcConfig config) {
        if (!claimNavigation(navigationLeases, npc.getUniqueId(), this::navigationPreempted)) return;
        FarmerPhase previousPhase = phase;
        long previousNextActionTick = nextActionTick;
        long previousNavigationStartedTick = navigationStartedTick;
        Location previousFishingWater = fishingWater;
        Location previousStandingTarget = standingTarget;
        Navigator navigator = null;
        try {
            navigationPause.resume();
            Target target = findTarget(center, config.fisher());
            if (target == null) {
                failedStandingTargets.clear();
                phase = FarmerPhase.RESTING;
                nextActionTick = serverTick + 200L;
                cancelAndReleaseNavigation(
                        navigationLeases, npc.getUniqueId(), npc.getNavigator());
                return;
            }
            fishingWater = target.water();
            standingTarget = target.standing();
            navigator = npc.getNavigator();
            startNavigation(navigator, standingTarget, config.navigationSpeedModifier());
            navigationStartedTick = serverTick;
            phase = FarmerPhase.GOING_TO_FISHING_SPOT;
        } catch (RuntimeException failure) {
            phase = previousPhase;
            nextActionTick = previousNextActionTick;
            navigationStartedTick = previousNavigationStartedTick;
            fishingWater = previousFishingWater;
            standingTarget = previousStandingTarget;
            try {
                if (navigator == null) releaseNavigation();
                else cancelAndReleaseNavigation(navigationLeases, npc.getUniqueId(), navigator);
            } catch (RuntimeException cleanupFailure) {
                appendFailure(failure, cleanupFailure);
            }
            throw failure;
        }
    }

    private void checkArrival(long serverTick, LivingNpcConfig config) {
        if (!ownsNavigation()) {
            if (!claimNavigation(navigationLeases, npc.getUniqueId(), this::navigationPreempted)) return;
            if (navigationPause.resume() && standingTarget != null) {
                resumeNavigationAfterPreemption(serverTick, config);
            }
        }
        if (standingTarget != null && fishingWater != null && isSafeFishingStanding(standingTarget.getBlock())
                && isSourceWater(fishingWater.getBlock())
                && horizontalDistanceSquared(npc.getEntity().getLocation(), standingTarget)
                        <= PRECISE_APPROACH_MARGIN * PRECISE_APPROACH_MARGIN
                && Math.abs(npc.getEntity().getLocation().getY() - standingTarget.getY()) <= 1.0) {
            holdRod();
            failedStandingTargets.clear();
            faceWater();
            phase = FarmerPhase.CASTING_LINE;
            nextActionTick = serverTick + 20L;
            cancelAndReleaseNavigation(
                    navigationLeases, npc.getUniqueId(), npc.getNavigator());
        } else if (!npc.getNavigator().isNavigating()
                || serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
            if (standingTarget != null) {
                NavigationRecovery.Result recovery = NavigationRecovery.recover(
                        npc, standingTarget, 2, serverTick, "GOING_TO_FISHING_SPOT",
                        config.navigationRetryBackoffTicks());
                if (recovery == NavigationRecovery.Result.RECOVERED) {
                    holdRod();
                    failedStandingTargets.clear();
                    faceWater();
                    phase = FarmerPhase.CASTING_LINE;
                    nextActionTick = serverTick + 20L;
                    cancelAndReleaseNavigation(navigationLeases, npc.getUniqueId(), npc.getNavigator());
                    return;
                }
                failedStandingTargets.add(targetKey(standingTarget));
            }
            teardownWorkState(
                    true, FarmerPhase.RESTING,
                    serverTick + config.navigationRetryBackoffTicks(), false);
        }
    }

    private void resumeNavigationAfterPreemption(long serverTick, LivingNpcConfig config) {
        Navigator navigator = null;
        try {
            navigator = npc.getNavigator();
            startNavigation(navigator, standingTarget, config.navigationSpeedModifier());
            navigationStartedTick = serverTick;
        } catch (RuntimeException failure) {
            try {
                if (navigator == null) releaseNavigation();
                else cancelAndReleaseNavigation(navigationLeases, npc.getUniqueId(), navigator);
            } catch (RuntimeException cleanupFailure) {
                appendFailure(failure, cleanupFailure);
            }
            throw failure;
        }
    }

    static boolean claimNavigation(
            NavigationLeaseManager leases, java.util.UUID npcUuid, Runnable onPreempt) {
        return leases.claim(npcUuid, NAVIGATION_OWNER, NAVIGATION_PRIORITY, onPreempt);
    }

    static void startNavigation(Navigator navigator, Location target, float speedModifier) {
        MovementService.startSimpleNavigation(
                navigator, target, speedModifier, PRECISE_APPROACH_MARGIN);
    }

    static void cancelAndReleaseNavigation(
            NavigationLeaseManager leases, java.util.UUID npcUuid, Navigator navigator) {
        try {
            if (leases.heldBy(npcUuid, NAVIGATION_OWNER) && navigator.isNavigating()) {
                navigator.cancelNavigation();
            }
        } finally {
            leases.release(npcUuid, NAVIGATION_OWNER);
        }
    }

    private boolean ownsNavigation() {
        return navigationLeases.heldBy(npc.getUniqueId(), NAVIGATION_OWNER);
    }

    private void releaseNavigation() {
        navigationLeases.release(npc.getUniqueId(), NAVIGATION_OWNER);
    }

    private void navigationPreempted() {
        navigationPause.pause();
    }

    static final class NavigationPause {
        private boolean paused;

        void pause() {
            paused = true;
        }

        boolean paused() {
            return paused;
        }

        boolean resume() {
            boolean wasPaused = paused;
            paused = false;
            return wasPaused;
        }
    }

    private void finishAttempt(long serverTick, LivingNpcConfig config, String villageId) {
        if (ThreadLocalRandom.current().nextDouble() < config.fisher().successChance()) {
            String fish = fishForRoll(ThreadLocalRandom.current().nextDouble());
            if (economy.addRoleProduction(
                    npc.getUniqueId(), villageId, ResidentRole.FISHER, fish,
                    1, config.fisher().maxCatchPerShift(), shiftKey)) {
                experienceAwarder.accept(10L);
                economy.recordActivity(npc.getUniqueId(), villageId, ResidentRole.FISHER, "Câu được cá", fish, 1);
            }
        }
        phase = FarmerPhase.CASTING_LINE;
        nextActionTick = serverTick + randomBetween(40L, 100L);
    }

    static String fishForRoll(double roll) {
        if (roll < 0.60) return "cod";
        if (roll < 0.85) return "salmon";
        if (roll < 0.98) return "pufferfish";
        return "tropical_fish";
    }

    static boolean requiresFishingRod(FarmerPhase phase) {
        return phase == FarmerPhase.CASTING_LINE || phase == FarmerPhase.WAITING_FOR_BITE
                || phase == FarmerPhase.REELING_IN;
    }

    private Target findTarget(Location center, FisherSettings settings) {
        java.util.Map<String, Target> targets = new java.util.LinkedHashMap<>();
        int radius = settings.waterSearchRadius();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -settings.waterSearchVerticalRange(); y <= settings.waterSearchVerticalRange(); y++) {
                        Block water = center.getWorld().getBlockAt(
                                center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                        if (!isSourceWater(water) || !water.getRelative(0, 1, 0).isPassable()) continue;
                        Location waterSurface = water.getLocation().add(0.5, 0.9, 0.5);
                        for (int sx = -3; sx <= 3; sx++) for (int sz = -3; sz <= 3; sz++) {
                            int distanceSquared = sx * sx + sz * sz;
                            if (distanceSquared < 4 || distanceSquared > 9) continue;
                            for (int sy = 2; sy >= -2; sy--) {
                                Block feet = water.getRelative(sx, sy, sz);
                                if (!isSafeFishingStanding(feet)) continue;
                                Location standing = feet.getLocation().add(0.5, 0, 0.5);
                                if (Math.abs(standing.getY() - waterSurface.getY()) > 2.5
                                        || !hasClearCast(standing, waterSurface)) continue;
                                String key = targetKey(standing);
                                if (!failedStandingTargets.contains(key)) {
                                    targets.putIfAbsent(key, new Target(standing, waterSurface));
                                }
                                break;
                            }
                        }
                }
            }
        }
        Location current = npc.getEntity().getLocation();
        return targets.values().stream()
                .min(java.util.Comparator.comparingDouble(target -> current.distanceSquared(target.standing())))
                .orElse(null);
    }

    static String targetKey(Location location) {
        return location.getWorld().getName() + ':' + location.getBlockX() + ':'
                + location.getBlockY() + ':' + location.getBlockZ();
    }

    private boolean hasClearCast(Location standing, Location water) {
        Location origin = standing.clone().add(0, 1.6, 0);
        org.bukkit.util.Vector velocity = castVelocity(origin, water);
        Location previous = origin;
        for (int tick = 1; tick <= 8; tick++) {
            Location point = origin.clone().add(velocity.clone().multiply(tick))
                    .add(0, -0.5 * 0.03 * tick * tick, 0);
            org.bukkit.util.Vector segment = point.toVector().subtract(previous.toVector());
            double distance = segment.length();
            if (distance > 0.0 && previous.getWorld().rayTraceBlocks(
                    previous, segment.normalize(), distance,
                    org.bukkit.FluidCollisionMode.NEVER, true) != null) return false;
            previous = point;
        }
        return true;
    }

    private boolean isSourceWater(Block block) {
        return block.getType() == Material.WATER
                && block.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
    }

    private boolean isSafeStanding(Block feet) {
        Material support = feet.getRelative(0, -1, 0).getType();
        return feet.isPassable() && !feet.isLiquid()
                && feet.getRelative(0, 1, 0).isPassable() && !feet.getRelative(0, 1, 0).isLiquid()
                && support.isSolid() && support != Material.MAGMA_BLOCK && support != Material.CAMPFIRE
                && support != Material.SOUL_CAMPFIRE && support != Material.CACTUS;
    }

    private boolean isSafeFishingStanding(Block feet) {
        if (!isSafeStanding(feet)) return false;
        for (org.bukkit.block.BlockFace face : HORIZONTAL_FACES) {
            Block adjacentFeet = feet.getRelative(face);
            if (!isSafeStanding(adjacentFeet)) return false;
        }
        return true;
    }

    private void faceWater() {
        if (fishingWater == null) return;
        Location eye = npc.getEntity() instanceof LivingEntity living
                ? living.getEyeLocation() : npc.getEntity().getLocation().add(0, 1.6, 0);
        org.bukkit.util.Vector velocity = castVelocity(eye, fishingWater);
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        float yaw = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
        float pitch = (float) -Math.toDegrees(Math.atan2(velocity.getY(), horizontal));
        npc.getEntity().setRotation(yaw, Math.clamp(pitch, -20.0f, 25.0f));
    }

    private boolean castLine(long serverTick) {
        clearHook();
        if (fishingWater == null || standingTarget == null || !(npc.getEntity() instanceof Player player)
                || !isSourceWater(fishingWater.getBlock())
                || horizontalDistanceSquared(player.getLocation(), standingTarget)
                        > PRECISE_APPROACH_MARGIN * PRECISE_APPROACH_MARGIN) return false;
        Location origin = player.getEyeLocation();
        org.bukkit.util.Vector velocity = castVelocity(origin, fishingWater);
        if (velocity.lengthSquared() <= 0.01) return false;
        try {
            hook = player.launchProjectile(FishHook.class, velocity);
            hook.setVelocity(castVelocity(hook.getLocation(), fishingWater));
            hook.setApplyLure(false);
            castLandingDeadline = serverTick + 20L;
            castStartedTick = serverTick;
            castLanded = false;
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return false;
        }
    }

    private void clearHook() {
        clearHook(false);
    }

    private void clearHook(boolean discardHandleOnFailure) {
        FishHook currentHook = hook;
        castLanded = false;
        if (currentHook == null) return;
        try {
            if (currentHook.isValid()) currentHook.remove();
            hook = null;
        } catch (RuntimeException exception) {
            if (discardHandleOnFailure) hook = null;
            throw exception;
        }
    }

    static org.bukkit.util.Vector castVelocity(Location origin, Location target) {
        double flightTicks = 8.0;
        org.bukkit.util.Vector delta = target.toVector().subtract(origin.toVector());
        return new org.bukkit.util.Vector(
                delta.getX() / flightTicks,
                (delta.getY() + 0.5 * 0.03 * flightTicks * flightTicks) / flightTicks,
                delta.getZ() / flightTicks);
    }

    static double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private boolean hookInSelectedWater() {
        if (hook == null || fishingWater == null) return false;
        Block hookBlock = hook.getLocation().getBlock();
        Block target = fishingWater.getBlock();
        return isSourceWater(hookBlock)
                && Math.abs(hookBlock.getX() - target.getX()) <= 1
                && Math.abs(hookBlock.getY() - target.getY()) <= 1
                && Math.abs(hookBlock.getZ() - target.getZ()) <= 1;
    }

    private void logHookFailure(String reason, long serverTick) {
        if (serverTick < nextHookTelemetryTick.getOrDefault(reason, 0L)) return;
        nextHookTelemetryTick.put(reason, serverTick + 100L);
        Location hookLocation = hook == null ? null : hook.getLocation();
        Block hookBlock = hookLocation == null ? null : hookLocation.getBlock();
        Block selectedBlock = fishingWater == null ? null : fishingWater.getBlock();
        int offsetX = hookBlock == null || selectedBlock == null ? Integer.MIN_VALUE
                : hookBlock.getX() - selectedBlock.getX();
        int offsetY = hookBlock == null || selectedBlock == null ? Integer.MIN_VALUE
                : hookBlock.getY() - selectedBlock.getY();
        int offsetZ = hookBlock == null || selectedBlock == null ? Integer.MIN_VALUE
                : hookBlock.getZ() - selectedBlock.getZ();
        NavigationDiagnostics.shared().logFisherHook(hookTelemetryMessage(
                npc.getUniqueId().toString(), reason,
                hook == null ? "null" : String.valueOf(hook.getState()),
                hook != null && hook.isValid(), hook != null && hook.isInWater(),
                blockPosition(hookBlock), blockPosition(selectedBlock),
                offsetX, offsetY, offsetZ, Math.max(0L, serverTick - castStartedTick)));
    }

    static String hookTelemetryMessage(
            String npcUuid, String reason, String hookState, boolean valid, boolean inWater,
            String hookBlock, String selectedBlock, int offsetX, int offsetY, int offsetZ, long ageTicks) {
        return "NPC_FISH_HOOK uuid=" + npcUuid + " reason=" + reason
                + " hookState=" + hookState + " valid=" + valid + " inWater=" + inWater
                + " hookBlock=" + hookBlock + " selectedBlock=" + selectedBlock
                + " offset=" + offset(offsetX) + "," + offset(offsetY) + "," + offset(offsetZ)
                + " ageTicks=" + ageTicks;
    }

    private static String blockPosition(Block block) {
        return block == null ? "unavailable"
                : block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static String offset(int value) {
        return value == Integer.MIN_VALUE ? "unavailable" : Integer.toString(value);
    }

    private void holdRod() {
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        if (!ownsHand) {
            ItemStack held = equipment.get(EquipmentSlot.HAND);
            previousHand = held == null ? null : held.clone();
            ownsHand = true;
        }
        equipment.set(EquipmentSlot.HAND, new ItemStack(Material.FISHING_ROD));
        if (npc.getEntity() instanceof LivingEntity living) {
            living.getEquipment().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
        }
    }

    private void clearHand() {
        clearHand(false);
    }

    private void clearHand(boolean discardSnapshotOnFailure) {
        if (!ownsHand) return;
        ItemStack handToRestore = previousHand;
        RuntimeException failure = null;
        try {
            npc.getOrAddTrait(Equipment.class).set(EquipmentSlot.HAND, handToRestore);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity living) {
                living.getEquipment().setItemInMainHand(handToRestore);
            }
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        if (failure == null || discardSnapshotOnFailure) {
            previousHand = null;
            ownsHand = false;
        }
        if (failure != null) throw failure;
    }

    private long randomBetween(long minimum, long maximum) {
        long boundedMaximum = Math.max(minimum, maximum);
        return ThreadLocalRandom.current().nextLong(minimum, boundedMaximum + 1L);
    }

    private record Target(Location standing, Location water) {
    }

    private static final org.bukkit.block.BlockFace[] HORIZONTAL_FACES = {
            org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.WEST
    };
}
