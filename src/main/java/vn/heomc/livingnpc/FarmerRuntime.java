package vn.heomc.livingnpc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

final class FarmerRuntime {
    private enum NavigationResult {
        IN_PROGRESS,
        ARRIVED,
        FAILED
    }

    private final NPC npc;
    private final NpcEconomy economy;
    private final WorldMutationPolicy mutationPolicy;
    private final VillageStore villageStore;
    private final VillagePathCache pathCache;
    private final SeatManager seatManager;
    private final ActivityPointManager activityPointManager;
    private final NavigationLeaseManager navigationLeases;
    private final java.util.function.LongConsumer experienceAwarder;
    private FarmerDefinition definition;
    private FarmerPhase phase = FarmerPhase.INACTIVE;
    private Deque<CropWork> workQueue;
    private CropWork currentWork;
    private long nextActionTick;
    private long navigationStartedTick;
    private long nextNavigationAttemptTick;
    private long lastScanTick;
    private boolean scanStaggerInitialized;
    private long nextAmbientTick;
    private Location navigationTarget;
    private Location navigationStage;
    private boolean inspecting;
    private int pendingDelivery;
    private Deque<Location> deliveryCandidates;
    private Location activeDeliveryChest;
    private UUID socialPartner;
    private String socialType;
    private boolean socialSpoken;
    private Long observedShiftKey;
    private long nextResidentPatrolTick;
    private Location sleepingBed;
    private boolean sleepActive;
    private String sleepDebug = "NOT_CHECKED";
    private SeatType seatingType;
    private FarmerPhase seatResumePhase;
    private long seatEndTick;
    private long standEndTick;
    private boolean lunchSeatCompleted;
    private long nextEatingAnimationTick;
    private boolean morningExitRequired;
    private long morningExitActiveTicks;
    private String navigationLeaseOwner;
    private GateRouteCoordinator gateRouteCoordinator;
    private WaypointRouteCoordinator waypointRouteCoordinator;

    FarmerRuntime(
            NPC npc,
            FarmerDefinition definition,
            NpcEconomy economy,
            WorldMutationPolicy mutationPolicy,
            VillageStore villageStore,
            VillagePathCache pathCache,
            SeatManager seatManager,
            ActivityPointManager activityPointManager,
            NavigationLeaseManager navigationLeases,
            java.util.function.LongConsumer experienceAwarder) {
        this.npc = npc;
        this.definition = definition;
        this.economy = economy;
        this.mutationPolicy = mutationPolicy;
        this.villageStore = villageStore;
        this.pathCache = pathCache;
        this.seatManager = seatManager;
        this.activityPointManager = activityPointManager;
        this.navigationLeases = navigationLeases;
        this.experienceAwarder = experienceAwarder;
    }

    FarmerRuntime(
            NPC npc, FarmerDefinition definition, NpcEconomy economy,
            WorldMutationPolicy mutationPolicy, VillageStore villageStore) {
        this(npc, definition, economy, mutationPolicy, villageStore,
                new VillagePathCache(villageStore), new SeatManager(villageStore),
                new ActivityPointManager(villageStore), new NavigationLeaseManager(), ignored -> { });
    }

    void updateDefinition(FarmerDefinition definition) {
        suspend();
        this.definition = definition;
    }

    void refreshDefinition(FarmerDefinition definition) {
        this.definition = definition;
    }

    FarmerPhase phase() {
        return phase;
    }

    UUID npcUuid() {
        return npc.getUniqueId();
    }

    boolean tickSleep(long serverTick, LivingNpcConfig config) {
        if (!npc.isSpawned() || !(npc.getEntity() instanceof HumanEntity human)) {
            seatManager.release(npc);
            sleepDebug = "NOT_SPAWNED";
            return false;
        }
        long time = human.getWorld().getTime();
        if (!isBedtime(time)) {
            sleepDebug = "NOT_BEDTIME";
            boolean wakingUp = sleepActive || human.isSleeping()
                    || phase == FarmerPhase.GOING_TO_BED || phase == FarmerPhase.SLEEPING;
            if (human.isSleeping()) human.wakeup(false);
            if (phase == FarmerPhase.GOING_TO_BED || phase == FarmerPhase.SLEEPING) reset();
            sleepingBed = null;
            sleepActive = false;
            if (wakingUp && config.seasonSix().enabled()) {
                morningExitRequired = true;
                morningExitActiveTicks = 0L;
                phase = FarmerPhase.WAKING_UP;
            }
            return false;
        }
        if (isSeatingPhase()) finishSeating();
        if (!definition.enabled(BehaviorFlag.MASTER)) {
            sleepDebug = "MASTER_OFF";
            if (human.isSleeping()) human.wakeup(false);
            if (phase == FarmerPhase.GOING_TO_BED || phase == FarmerPhase.SLEEPING) reset();
            sleepingBed = null;
            sleepActive = false;
            return false;
        }
        if (human.isSleeping()) {
            sleepDebug = "SLEEPING";
            sleepActive = true;
            phase = FarmerPhase.SLEEPING;
            return true;
        }
        sleepActive = true;
        clearHand();
        stopInspection();
        clearSocial();
        if (phase == FarmerPhase.GOING_TO_BED) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.IN_PROGRESS) {
                sleepDebug = "GOING_TO_BED";
                return true;
            }
            if (result == NavigationResult.ARRIVED && sleep(human)) return true;
            if (result == NavigationResult.ARRIVED) {
                sleepDebug = "SLEEP_REJECTED";
                sleepActive = false;
                phase = FarmerPhase.INACTIVE;
                sleepingBed = null;
                return true;
            }
            if (result == NavigationResult.FAILED) {
                sleepDebug = "BED_NAVIGATION_FAILED";
                // NavigationRecovery giữ intent; giữ bed/phase để retry sau backoff.
                return true;
            }
            sleepDebug = "SLEEP_REJECTED";
            phase = FarmerPhase.INACTIVE;
            sleepingBed = null;
        }
        if (serverTick < nextNavigationAttemptTick) {
            sleepDebug = "RETRY_COOLDOWN";
            return true;
        }
        Location home = definition.home().resolve();
        if (home == null) {
            sleepDebug = "HOME_UNRESOLVED";
            return true;
        }
        if (!human.getWorld().equals(home.getWorld())) {
            sleepDebug = "HOME_DIFFERENT_WORLD";
            return true;
        }
        BedLookup bedLookup = findBed(home);
        sleepingBed = bedLookup.location();
        if (sleepingBed == null) {
            sleepDebug = bedLookup.debug();
            restAtHome(serverTick, config, home);
            return true;
        }
        Location standing = findNearestStandingLocation(sleepingBed, human.getLocation());
        if (standing == null) {
            sleepDebug = "NO_SAFE_BED_STANDING_BLOCK";
            sleepingBed = null;
            restAtHome(serverTick, config, home);
            return true;
        }
        if (human.getLocation().distanceSquared(standing)
                <= config.navigationDistanceMargin() * config.navigationDistanceMargin()) {
            if (!sleep(human)) {
                sleepDebug = "SLEEP_REJECTED";
                restAtHome(serverTick, config, home);
            }
        } else {
            sleepDebug = navigate(standing, FarmerPhase.GOING_TO_BED, serverTick, config)
                    ? "GOING_TO_BED" : "RETRY_COOLDOWN";
        }
        return true;
    }

    boolean sleeping() {
        return sleepActive;
    }

    String sleepDebug() {
        return sleepDebug;
    }

    Location sleepingBed() {
        return sleepingBed;
    }

    static boolean isBedtime(long worldTime) {
        return worldTime >= 13000L && worldTime < 23000L;
    }

    boolean availableForSocial(LivingNpcConfig config) {
        if (!npc.isSpawned() || definition.villageId() == null || !definition.enabled(BehaviorFlag.MASTER)) {
            return false;
        }
        Location location = npc.getEntity().getLocation();
        boolean offShift = definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isScheduledTime(location.getWorld().getTime(), schedule(config));
        return offShift && !location.getWorld().hasStorm() && !hasNearbyDanger(location, config)
                && (phase == FarmerPhase.INACTIVE || isAmbientPhase());
    }

    boolean startSocial(long serverTick, LivingNpcConfig config, String type, Location point, UUID partner) {
        if (!availableForSocial(config) || point == null
                || !npc.getEntity().getWorld().equals(point.getWorld())) {
            return false;
        }
        Location target = randomWanderTarget(point, 2);
        if (target == null) target = point;
        socialPartner = partner;
        socialType = type;
        socialSpoken = false;
        return navigate(target, type.equals("cho") ? FarmerPhase.GOING_TO_MARKET : FarmerPhase.GOING_TO_SCENIC,
                serverTick, config);
    }

    void cancelSocial() {
        if (phase == FarmerPhase.GOING_TO_MARKET || phase == FarmerPhase.GOING_TO_SCENIC
                || phase == FarmerPhase.SHOPPING || phase == FarmerPhase.SOCIALIZING) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            navigationTarget = null;
            navigationStage = null;
            phase = FarmerPhase.INACTIVE;
        }
        clearSocial();
    }

    void tick(long serverTick, LivingNpcConfig config) {
        if (!npc.isSpawned()) {
            seatManager.release(npc);
            return;
        }
        if (!definition.enabled(BehaviorFlag.MASTER)) {
            suspend();
            return;
        }
        Location npcLocation = npc.getEntity().getLocation();
        if (!RuntimeChunkAvailability.loaded(npcLocation)) {
            suspend();
            return;
        }
        if (definition.activeRole() == ResidentRole.FARMER && definition.plot() != null) {
            Location plot = definition.plot().resolve();
            int plotRadius = Math.min(definition.plotRadius(), config.maxPlotRadius());
            if (plot == null || !RuntimeChunkAvailability.loadedArea(plot, plotRadius)) {
                suspend();
                return;
            }
        }
        updateLookPose();
        Collection<Player> nearbyPlayers = npcLocation.getWorld().getNearbyPlayers(npcLocation, config.activationRange());

        if (handleMorningExit(serverTick, config)) return;
        if (handleSocial(serverTick, config)) return;

        if (definition.activeRole() == ResidentRole.RESIDENT) {
            clearHand();
            if (definition.enabled(BehaviorFlag.AVOID_MONSTERS) && hasNearbyDanger(npcLocation, config)) {
                goHome(serverTick, config, FarmerPhase.SHELTERING);
            } else {
                residentLife(serverTick, config, nearbyPlayers);
            }
            return;
        }
        if (definition.activeRole() != ResidentRole.FARMER) {
            suspend();
            return;
        }

        if (definition.enabled(BehaviorFlag.AVOID_MONSTERS) && hasNearbyDanger(npcLocation, config)) {
            goHome(serverTick, config, FarmerPhase.SHELTERING);
            return;
        }

        ResidentSchedule schedule = definition.schedule(
                ResidentRole.FARMER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        boolean scheduledNow = !definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                || SchedulePolicy.isScheduledTime(npcLocation.getWorld().getTime(), schedule);
        trackShiftTransition(npcLocation, config, schedule, scheduledNow);
        boolean lunchBreak = definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && FarmerDailyPlan.isLunchBreak(
                        npcLocation.getWorld().getTime(), schedule, config.farmerDailyPlan());
        if (lunchBreak) {
            takeLunchBreak(serverTick, config, nearbyPlayers);
            return;
        }
        lunchSeatCompleted = false;
        if (isSeatingPhase() && seatingType == SeatType.DINING) {
            startStanding(serverTick, config, FarmerPhase.GOING_TO_PLOT);
            tickSeating(serverTick, config);
            return;
        }
        if (phase == FarmerPhase.LUNCH_BREAK) {
            if (npc.getNavigator().isNavigating()) {
                npc.getNavigator().cancelNavigation();
            }
            navigationTarget = null;
            navigationStage = null;
            phase = FarmerPhase.INACTIVE;
        }
        boolean workTime = definition.plot() != null
                && definition.villageId() != null
                && villageStore.configuredDeliveryChest(definition.villageId()) != null
                && definition.enabled(BehaviorFlag.HARVEST)
                && definition.enabled(BehaviorFlag.PLANT)
                && (!definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                        || SchedulePolicy.isWorkTime(
                                npcLocation.getWorld().getTime(),
                                npcLocation.getWorld().hasStorm(),
                                schedule));
        if (!workTime) {
            idleAtHome(serverTick, config, nearbyPlayers);
            return;
        }

        if (phase == FarmerPhase.GOING_TO_PLOT || phase == FarmerPhase.FINDING_WORK
                || phase == FarmerPhase.GOING_TO_CROP || phase == FarmerPhase.INSPECTING) {
            holdHoe();
        }

        Location plot = definition.plot().resolve();
        if (plot == null) {
            suspend();
            return;
        }
        if (!npcLocation.getWorld().equals(plot.getWorld())) {
            goHome(serverTick, config, FarmerPhase.GOING_HOME);
            return;
        }

        if (handleDelivery(serverTick, config, plot)) {
            return;
        }

        if (phase == FarmerPhase.INACTIVE || phase == FarmerPhase.GOING_HOME || phase == FarmerPhase.SHELTERING) {
            if (serverTick < nextNavigationAttemptTick) return;
            Location plotEntry = findPlotEntry(plot, npcLocation, definition.plotRadius());
            if (plotEntry != null) {
                holdHoe();
                navigate(plotEntry, FarmerPhase.GOING_TO_PLOT, serverTick, config);
            } else {
                nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
            }
            return;
        }
        if (phase == FarmerPhase.GOING_TO_PLOT) {
            if (navigationTarget == null) {
                if (requiresPlotEntry(npcLocation, plot, definition.plotRadius())) {
                    if (serverTick >= nextNavigationAttemptTick) {
                        Location plotEntry = findPlotEntry(plot, npcLocation, definition.plotRadius());
                        if (plotEntry != null) {
                            navigate(plotEntry, FarmerPhase.GOING_TO_PLOT, serverTick, config);
                        }
                    }
                } else {
                    holdHoe();
                    phase = FarmerPhase.FINDING_WORK;
                }
                return;
            }
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.ARRIVED) {
                if (requiresPlotEntry(npc.getEntity().getLocation(), plot, definition.plotRadius())) {
                    nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
                } else {
                    holdHoe();
                    phase = FarmerPhase.FINDING_WORK;
                }
            } else if (result == NavigationResult.FAILED) {
                phase = FarmerPhase.INACTIVE;
            }
            return;
        }
        if (isAmbientPhase()) {
            finishAmbientWhenReady(serverTick);
            return;
        }
        if (phase == FarmerPhase.FINDING_WORK) {
            if (requiresPlotEntry(npcLocation, plot, definition.plotRadius())) {
                if (serverTick < nextNavigationAttemptTick) return;
                Location plotEntry = findPlotEntry(plot, npcLocation, definition.plotRadius());
                if (plotEntry != null) {
                    navigate(plotEntry, FarmerPhase.GOING_TO_PLOT, serverTick, config);
                } else {
                    nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
                }
                return;
            }
            findWork(serverTick, config, plot, nearbyPlayers);
            return;
        }
        if (phase == FarmerPhase.GOING_TO_CROP) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result != NavigationResult.IN_PROGRESS) {
                if (currentWork != null
                        && result == NavigationResult.ARRIVED
                        && npc.getEntity().getWorld().equals(currentWork.location().getWorld())
                        && npc.getEntity().getLocation().distanceSquared(currentWork.location()) <= 9.0) {
                    inspectWork(serverTick, config);
                } else {
                    currentWork = null;
                    phase = FarmerPhase.FINDING_WORK;
                }
            }
            return;
        }
        if (phase == FarmerPhase.INSPECTING && serverTick >= nextActionTick) {
            prepareWork(serverTick, config);
            return;
        }
        if (phase == FarmerPhase.WORKING && serverTick >= nextActionTick) {
            performWork(serverTick, config);
        }
    }

    private void goHome(long serverTick, LivingNpcConfig config, FarmerPhase travelPhase) {
        if (isSeatingPhase()) finishSeating();
        stopInspection();
        clearHand();
        Location home = homeTarget();
        if (home == null) {
            suspend();
            return;
        }
        Location current = npc.getEntity().getLocation();
        if (!current.getWorld().equals(home.getWorld())) {
            suspend();
            return;
        }
        if (phase != travelPhase && current.distanceSquared(home) > 4.0) {
            navigate(home, travelPhase, serverTick, config);
        } else if (phase == travelPhase && navigationTarget == null) {
            // Citizens có thể trả path=absent ngay lần đầu. Giữ ý định về nhà,
            // chờ backoff rồi retry; không rơi về INACTIVE để tạo vòng lặp 10 tick.
            if (serverTick >= nextNavigationAttemptTick) {
                navigate(home, travelPhase, serverTick, config);
            }
        } else if (phase == travelPhase) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result != NavigationResult.IN_PROGRESS) {
                clearHand();
                if (result == NavigationResult.ARRIVED) {
                    phase = FarmerPhase.INACTIVE;
                }
            }
        } else if (phase != travelPhase) {
            clearHand();
            reset();
        }
    }

    private void restAtHome(long serverTick, LivingNpcConfig config, Location home) {
        Location target = findRestLocation(home, npc.getEntity().getLocation());
        if (target == null) {
            phase = FarmerPhase.RESTING;
            return;
        }
        double margin = config.navigationDistanceMargin();
        if (navigationTargetReached(npc.getEntity().getLocation(), target, margin)) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            navigationTarget = null;
            navigationStage = null;
            phase = FarmerPhase.RESTING;
            return;
        }
        if (phase == FarmerPhase.GOING_HOME && navigationTarget != null) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.IN_PROGRESS) return;
            if (result == NavigationResult.ARRIVED) {
                phase = FarmerPhase.RESTING;
                return;
            }
            phase = FarmerPhase.RESTING;
            return;
        }
        if (serverTick < nextNavigationAttemptTick) return;
        navigate(target, FarmerPhase.GOING_HOME, serverTick, config);
    }

    private void idleAtHome(
            long serverTick, LivingNpcConfig config, Collection<Player> nearbyPlayers) {
        Location home = homeTarget();
        if (home == null) {
            suspend();
            return;
        }
        Location current = npc.getEntity().getLocation();
        if (!current.getWorld().equals(home.getWorld())) {
            suspend();
            return;
        }
        if (current.distanceSquared(home) > 16.0) {
            goHome(serverTick, config, FarmerPhase.GOING_HOME);
            return;
        }
        if (phase == FarmerPhase.GOING_HOME) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.IN_PROGRESS) {
                return;
            }
        }
        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
        navigationTarget = null;
        navigationStage = null;
        clearSocial();
        phase = FarmerPhase.RESTING;
    }

    private void takeLunchBreak(
            long serverTick, LivingNpcConfig config, Collection<Player> nearbyPlayers) {
        stopInspection();
        clearHand();
        currentWork = null;
        workQueue = null;
        Location home = homeTarget();
        if (home == null) {
            suspend();
            return;
        }
        Location current = npc.getEntity().getLocation();
        if (!current.getWorld().equals(home.getWorld())) {
            suspend();
            return;
        }
        if (isSeatingPhase()) {
            tickSeating(serverTick, config);
            return;
        }
        if (!lunchSeatCompleted && config.seating().enabled()
                && beginSeating(serverTick, config, SeatType.DINING, FarmerPhase.LUNCH_BREAK)) {
            return;
        }
        if (phase != FarmerPhase.LUNCH_BREAK) {
            if (npc.getNavigator().isNavigating()) {
                npc.getNavigator().cancelNavigation();
            }
            navigationTarget = null;
            navigationStage = null;
            deliveryCandidates = null;
            activeDeliveryChest = null;
            clearSocial();
            phase = FarmerPhase.LUNCH_BREAK;
        }
        if (current.distanceSquared(home) > 16.0) {
            if (navigationTarget == null) {
                navigate(home, FarmerPhase.LUNCH_BREAK, serverTick, config);
            } else {
                NavigationResult result = navigationResult(serverTick, config);
                if (result == NavigationResult.FAILED) {
                    phase = FarmerPhase.LUNCH_BREAK;
                }
            }
            return;
        }
        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
            navigationTarget = null;
            navigationStage = null;
        }
        if (serverTick >= nextAmbientTick) {
            startLunchAmbient(serverTick, config, nearbyPlayers);
        }
    }

    private void startLunchAmbient(
            long serverTick, LivingNpcConfig config, Collection<Player> nearbyPlayers) {
        Player player = definition.enabled(BehaviorFlag.WATCH_PLAYERS)
                ? nearestVisiblePlayer(nearbyPlayers, config.playerNoticeRange()) : null;
        if (player != null) {
            faceHorizontal(player.getEyeLocation());
        } else if (definition.enabled(BehaviorFlag.LOOK_AROUND)) {
            faceRandomDirection();
        }
        phase = FarmerPhase.LUNCH_BREAK;
        nextAmbientTick = serverTick + randomBetween(
                config.ambientIntervalMinTicks(), config.ambientIntervalMaxTicks());
    }

    private void residentLife(
            long serverTick, LivingNpcConfig config, Collection<Player> nearbyPlayers) {
        Location home = homeTarget();
        if (home == null || !npc.getEntity().getWorld().equals(home.getWorld())) {
            suspend();
            return;
        }
        Location current = npc.getEntity().getLocation();
        long worldTime = current.getWorld().getTime();
        boolean stayHome = current.getWorld().hasStorm() || worldTime >= 12000L;
        if (isSeatingPhase()) {
            if (stayHome) startStanding(serverTick, config, FarmerPhase.INACTIVE);
            tickSeating(serverTick, config);
            return;
        }
        if (stayHome) {
            idleAtHome(serverTick, config, nearbyPlayers);
            return;
        }
        if (phase == FarmerPhase.WANDERING) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.IN_PROGRESS) return;
            if (config.seating().enabled() && definition.enabled(BehaviorFlag.REST)
                    && beginSeating(serverTick, config, SeatType.REST, FarmerPhase.INACTIVE)) {
                return;
            }
            phase = FarmerPhase.RESTING;
            nextActionTick = serverTick + randomBetween(40, 100);
            nextResidentPatrolTick = serverTick + randomBetween(
                    (int) config.residentPatrol().tripCooldownMinTicks(),
                    (int) config.residentPatrol().tripCooldownMaxTicks());
            return;
        }
        if (phase == FarmerPhase.RESTING || phase == FarmerPhase.LOOKING_AROUND
                || phase == FarmerPhase.WATCHING_PLAYER) {
            if (serverTick < nextActionTick) return;
            phase = FarmerPhase.INACTIVE;
        }
        if (definition.enabled(BehaviorFlag.WANDER) && serverTick >= nextResidentPatrolTick) {
            Location target = pathCache.target(definition.villageId(), current, config.residentPatrol());
            if (target == null) {
                target = randomWanderTarget(home, Math.min(12, config.residentPatrol().maxTargetDistance()));
            }
            if (target != null) {
                navigate(target, FarmerPhase.WANDERING, serverTick, config);
                return;
            }
            nextResidentPatrolTick = serverTick + config.navigationRetryBackoffTicks();
        }
        if (serverTick >= nextAmbientTick) {
            Player player = definition.enabled(BehaviorFlag.WATCH_PLAYERS)
                    ? nearestVisiblePlayer(nearbyPlayers, config.playerNoticeRange()) : null;
            if (player != null) {
                faceHorizontal(player.getEyeLocation());
                phase = FarmerPhase.WATCHING_PLAYER;
            } else if (definition.enabled(BehaviorFlag.LOOK_AROUND)) {
                faceRandomDirection();
                phase = FarmerPhase.LOOKING_AROUND;
            } else {
                if (config.seating().enabled() && definition.enabled(BehaviorFlag.REST)
                        && beginSeating(serverTick, config, SeatType.REST, FarmerPhase.INACTIVE)) {
                    return;
                }
                phase = FarmerPhase.RESTING;
            }
            nextActionTick = serverTick + randomBetween(
                    config.ambientDurationMinTicks(), config.ambientDurationMaxTicks());
            scheduleAmbient(serverTick, config);
        }
    }

    private boolean handleMorningExit(long serverTick, LivingNpcConfig config) {
        if (!morningExitRequired || !config.seasonSix().enabled()) return false;
        morningExitActiveTicks += config.tickInterval();
        if (morningExitActiveTicks >= config.seasonSix().morningExitTimeoutTicks()) {
            finishMorningExit();
            return false;
        }
        if (phase == FarmerPhase.MORNING_ACTIVITY) {
            finishMorningExit();
            return false;
        }
        if (phase == FarmerPhase.LEAVING_HOME) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.IN_PROGRESS) return true;
            if (result == NavigationResult.ARRIVED) {
                phase = FarmerPhase.MORNING_ACTIVITY;
                return true;
            }
            activityPointManager.release(npc.getUniqueId());
        }
        if (serverTick < nextNavigationAttemptTick) return true;
        Location current = npc.getEntity().getLocation();
        ActivityPoint exit = activityPointManager.reserveClosest(
                npc.getUniqueId(), definition.villageId(), ActivityPointType.HOME_EXIT,
                definition.activeRole(), current.getWorld().getTime(), current,
                target -> npc.getNavigator().canNavigateTo(
                        target, LivingNavigation.allowDoors(npc.getNavigator().getLocalParameters())));
        Location target = exit == null ? findMorningExitFallback() : exit.standing().resolve();
        if (target == null || !navigate(target, FarmerPhase.LEAVING_HOME, serverTick, config)) {
            activityPointManager.release(npc.getUniqueId());
            nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
        }
        return true;
    }

    private Location findMorningExitFallback() {
        Location home = definition.home().resolve();
        if (home == null || !home.getWorld().equals(npc.getEntity().getWorld())) return null;
        Location current = npc.getEntity().getLocation();
        for (int radius = 3; radius <= 6; radius++) {
            java.util.ArrayList<Location> candidates = new java.util.ArrayList<>();
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                for (int y = 2; y >= -2; y--) {
                    Block feet = home.getWorld().getBlockAt(
                            home.getBlockX() + x, home.getBlockY() + y, home.getBlockZ() + z);
                    if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                            && feet.getRelative(0, -1, 0).getType().isSolid()) {
                        candidates.add(feet.getLocation().add(0.5, 0, 0.5));
                    }
                }
            }
            Location entry = nearestCandidate(candidates, current);
            if (entry != null) return entry;
        }
        return null;
    }

    private void finishMorningExit() {
        activityPointManager.release(npc.getUniqueId());
        releaseNavigationLease();
        morningExitRequired = false;
        morningExitActiveTicks = 0L;
        phase = FarmerPhase.INACTIVE;
    }

    private void findWork(long serverTick, LivingNpcConfig config, Location plot, Collection<Player> nearbyPlayers) {
        int carriedSlots = economy.carriedSlotCount(npc.getUniqueId());
        if (pendingDelivery > 0
                && carriedSlots >= NpcEconomy.CARRIED_DEPOSIT_SLOT_THRESHOLD
                && startDelivery(serverTick, config)) {
            economyLog("INVENTORY_THRESHOLD_REACHED slots=" + carriedSlots + "/" + NpcEconomy.CARRIED_SLOTS);
            return;
        }
        if (workQueue == null || workQueue.isEmpty()) {
            if (pendingDelivery > 0 && startDelivery(serverTick, config)) {
                return;
            }
            if (!scanStaggerInitialized) {
                lastScanTick = serverTick
                        - Math.floorMod(npc.getUniqueId().hashCode(), (int) config.workScanIntervalTicks());
                scanStaggerInitialized = true;
            }
            if (serverTick - lastScanTick < config.workScanIntervalTicks()) {
                if (serverTick >= nextAmbientTick) {
                    startAmbient(serverTick, config, plot, nearbyPlayers);
                }
                return;
            }
            int radius = Math.clamp(definition.plotRadius(), 1, config.maxPlotRadius());
            workQueue = CropScanner.scan(plot, radius);
            lastScanTick = serverTick;
        }
        do {
            currentWork = workQueue.peekFirst();
            if (currentWork != null && !isWorkEnabled(currentWork)) {
                workQueue.pollFirst();
            }
        } while (currentWork != null && !isWorkEnabled(currentWork));
        if (currentWork == null) {
            if (pendingDelivery > 0 && startDelivery(serverTick, config)) {
                return;
            }
            scheduleAmbient(serverTick, config);
            return;
        }
        Location standingTarget = findStandingLocation(currentWork.location(), npc.getEntity().getLocation());
        if (standingTarget == null) {
            workQueue.pollFirst();
            currentWork = null;
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        if (navigate(standingTarget, FarmerPhase.GOING_TO_CROP, serverTick, config)) {
            workQueue.pollFirst();
        }
    }

    private void inspectWork(long serverTick, LivingNpcConfig config) {
        if (currentWork == null) {
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        faceBlock(currentWork.location());
        npc.setSneaking(true);
        inspecting = true;
        nextActionTick = serverTick + scaledDelay(config.inspectionDurationTicks());
        phase = FarmerPhase.INSPECTING;
    }

    private void prepareWork(long serverTick, LivingNpcConfig config) {
        npc.setSneaking(false);
        inspecting = false;
        Material held = currentWork.type() == CropWork.Type.PLANT ? seedFor(currentWork.crop()) : Material.IRON_HOE;
        setHand(new ItemStack(held));
        nextActionTick = serverTick + Math.max(5L, scaledDelay(config.inspectionDurationTicks() / 2L));
        phase = FarmerPhase.WORKING;
    }

    private void performWork(long serverTick, LivingNpcConfig config) {
        if (currentWork == null || !isWorkEnabled(currentWork)) {
            currentWork = null;
            clearHand();
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        Block block = currentWork.location().getBlock();
        if (!canPerformAt(block, config)) {
            currentWork = null;
            clearHand();
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        long shiftKey = SchedulePolicy.activeShiftKey(block.getWorld().getFullTime(), schedule(config));
        int seedSurplus = currentWork.type() == CropWork.Type.HARVEST && currentWork.crop() == Material.WHEAT
                ? wheatSeedSurplus(block.getDrops(new ItemStack(Material.IRON_HOE)).stream()
                        .filter(drop -> drop.getType() == Material.WHEAT_SEEDS)
                        .mapToInt(ItemStack::getAmount)
                        .sum())
                : 0;
        if (currentWork.type() == CropWork.Type.HARVEST
                && !economy.canAcceptHarvest(
                        npc.getUniqueId(), definition.villageId(), config.outputPerAction(), seedSurplus, shiftKey)) {
            currentWork = null;
            clearHand();
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        if (npc.getEntity() instanceof LivingEntity living) {
            faceBlock(block.getLocation());
            living.swingMainHand();
        }
        boolean actionSucceeded = false;
        boolean produced = false;
        org.bukkit.block.data.BlockData previousCropData = null;
        if (currentWork.type() == CropWork.Type.HARVEST
                && CropScanner.isAllowedCrop(block.getType())
                && block.getType() == currentWork.crop()
                && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() == ageable.getMaximumAge()
                && mutationPolicy.allows(block.getLocation(), MutationType.PLACE)) {
            Ageable replanted = (Ageable) ageable.clone();
            previousCropData = block.getBlockData().clone();
            replanted.setAge(0);
            block.setBlockData(replanted, true);
            actionSucceeded = true;
            produced = true;
        } else if (currentWork.type() == CropWork.Type.PLANT
                && block.isEmpty()
                && block.getRelative(0, -1, 0).getType() == Material.FARMLAND
                && mutationPolicy.allows(block.getLocation(), MutationType.PLACE)) {
            block.setType(currentWork.crop(), true);
            actionSucceeded = true;
        }
        if (produced) {
            String output = harvestOutput(currentWork.crop());
            if (output != null) {
                boolean accepted = economy.addCarriedProduction(
                        npc.getUniqueId(), output, config.outputPerAction(), shiftKey);
                if (accepted) {
                    pendingDelivery += config.outputPerAction();
                    economy.recordActivity(
                            npc.getUniqueId(), definition.villageId(), ResidentRole.FARMER,
                            "Thu hoạch", output, config.outputPerAction());
                    if (seedSurplus > 0 && economy.addCarriedLoot(
                            npc.getUniqueId(), java.util.Map.of("wheat_seeds", Math.min(seedSurplus, NpcEconomy.CARRIED_STACK_SIZE)))) {
                        pendingDelivery += Math.min(seedSurplus, NpcEconomy.CARRIED_STACK_SIZE);
                    }
                } else if (previousCropData != null) {
                    block.setBlockData(previousCropData, true);
                    actionSucceeded = false;
                    produced = false;
                    economyLog("harvest_rollback reason=OUTPUT_REJECTED crop=" + currentWork.crop());
                }
            }
        }
        if (actionSucceeded) {
            experienceAwarder.accept(10L);
            if (!produced) {
                economy.recordActivity(
                        npc.getUniqueId(), definition.villageId(), ResidentRole.FARMER,
                        "Trồng cây", harvestOutput(currentWork.crop()), 1);
            }
        }
        currentWork = null;
        holdHoe();
        phase = FarmerPhase.FINDING_WORK;
        if (workQueue != null && !workQueue.isEmpty()) {
            return;
        }
        scheduleAmbient(serverTick, config);
        if (definition.enabled(BehaviorFlag.REST)) {
            nextActionTick = serverTick + randomDelay(config);
            phase = FarmerPhase.RESTING;
        } else {
            phase = FarmerPhase.FINDING_WORK;
        }
    }

    private boolean canPerformAt(Block block, LivingNpcConfig config) {
        Location plot = definition.plot() == null ? null : definition.plot().resolve();
        Location current = npc.getEntity().getLocation();
        if (plot == null
                || !block.getWorld().equals(plot.getWorld())
                || !current.getWorld().equals(block.getWorld())
                || current.distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) > 9.0) {
            return false;
        }
        int radius = Math.min(definition.plotRadius(), config.maxPlotRadius());
        if (Math.abs(block.getX() - plot.getBlockX()) > radius
                || Math.abs(block.getZ() - plot.getBlockZ()) > radius
                || Math.abs(block.getY() - plot.getBlockY()) > 2) {
            return false;
        }
        MutationType mutation = currentWork.type() == CropWork.Type.HARVEST
                ? MutationType.BREAK
                : MutationType.PLACE;
        return mutationPolicy.allows(block.getLocation(), mutation);
    }

    private void startAmbient(long serverTick, LivingNpcConfig config, Location plot, Collection<Player> nearbyPlayers) {
        Player player = definition.enabled(BehaviorFlag.WATCH_PLAYERS)
                ? nearestVisiblePlayer(nearbyPlayers, config.playerNoticeRange())
                : null;
        AmbientAction action = AmbientPolicy.choose(
                ThreadLocalRandom.current().nextInt(100),
                player != null,
                definition.enabled(BehaviorFlag.WANDER) && config.wanderRadius() > 0,
                definition.enabled(BehaviorFlag.LOOK_AROUND),
                definition.enabled(BehaviorFlag.REST));
        if (action == null) {
            scheduleAmbient(serverTick, config);
            return;
        }
        nextActionTick = serverTick + randomBetween(config.ambientDurationMinTicks(), config.ambientDurationMaxTicks());
        switch (action) {
            case WATCH_PLAYER -> {
                faceHorizontal(player.getEyeLocation());
                phase = FarmerPhase.WATCHING_PLAYER;
            }
            case WANDER -> {
                Location target = randomWanderTarget(plot, config.wanderRadius());
                if (target != null) {
                    navigate(target, FarmerPhase.WANDERING, serverTick, config);
                } else {
                    phase = FarmerPhase.RESTING;
                }
            }
            case LOOK_AROUND -> {
                faceRandomDirection();
                phase = FarmerPhase.LOOKING_AROUND;
            }
            case REST -> phase = FarmerPhase.RESTING;
        }
        scheduleAmbient(serverTick, config);
    }

    private void finishAmbientWhenReady(long serverTick) {
        if (phase == FarmerPhase.WANDERING && npc.getNavigator().isNavigating() && serverTick < nextActionTick) {
            return;
        }
        if (serverTick < nextActionTick) {
            return;
        }
        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
        phase = FarmerPhase.FINDING_WORK;
    }

    private boolean isAmbientPhase() {
        return phase == FarmerPhase.RESTING
                || phase == FarmerPhase.LOOKING_AROUND
                || phase == FarmerPhase.WANDERING
                || phase == FarmerPhase.WATCHING_PLAYER;
    }

    private BedLookup findBed(Location home) {
        if (!(home.getBlock().getBlockData() instanceof Bed bed)) {
            return new BedLookup(null, "BED_BLOCK_MISSING");
        }
        Block block = home.getBlock();
        if (bed.getPart() == Bed.Part.FOOT) block = block.getRelative(bed.getFacing());
        if (!(block.getBlockData() instanceof Bed assigned)) {
            return new BedLookup(null, "BED_CANONICAL_HALF_INVALID");
        }
        if (assigned.isOccupied() && !clearStaleBedOccupancy(block, assigned)) {
            return new BedLookup(null, "BED_OCCUPIED");
        }
        return new BedLookup(block.getLocation(), "BED_READY");
    }

    private boolean clearStaleBedOccupancy(Block head, Bed headData) {
        Location center = head.getLocation().add(0.5, 0.5, 0.5);
        boolean sleeperNearby = head.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0).stream()
                .anyMatch(entity -> entity instanceof HumanEntity human && human.isSleeping()
                        || entity instanceof Villager villager && villager.isSleeping());
        if (sleeperNearby) return false;

        headData.setOccupied(false);
        head.setBlockData(headData, false);
        Block foot = head.getRelative(headData.getFacing().getOppositeFace());
        if (foot.getBlockData() instanceof Bed footData) {
            footData.setOccupied(false);
            foot.setBlockData(footData, false);
        }
        return true;
    }

    private Location homeTarget() {
        Location home = definition.home().resolve();
        if (home == null || !(home.getBlock().getBlockData() instanceof Bed)) return home;
        return findStandingLocation(home, npc.getEntity().getLocation());
    }

    private Location findRestLocation(Location home, Location current) {
        Location standing = findNearestStandingLocation(home, current);
        if (standing != null) return standing;
        java.util.ArrayList<Location> candidates = new java.util.ArrayList<>();
        for (int radius = 1; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                for (int y = 2; y >= -2; y--) {
                    Block feet = home.getWorld().getBlockAt(
                            home.getBlockX() + x, home.getBlockY() + y, home.getBlockZ() + z);
                    if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                            && feet.getRelative(0, -1, 0).getType().isSolid()) {
                        candidates.add(feet.getLocation().add(0.5, 0, 0.5));
                    }
                }
            }
            Location nearest = nearestCandidate(candidates, current);
            if (nearest != null) return nearest;
        }
        return null;
    }

    private boolean sleep(HumanEntity human) {
        if (sleepingBed == null || !(sleepingBed.getBlock().getBlockData() instanceof Bed bed)
                || bed.isOccupied() || !human.getWorld().equals(sleepingBed.getWorld())
                || !RuntimeChunkAvailability.loaded(sleepingBed)
                || findNearestStandingLocation(sleepingBed, human.getLocation()) == null
                || !navigationTargetReached(human.getLocation(),
                        findNearestStandingLocation(sleepingBed, human.getLocation()), 1.0)) return false;
        if (!human.sleep(sleepingBed, true)) return false;
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        navigationTarget = null;
        navigationStage = null;
        phase = FarmerPhase.SLEEPING;
        sleepDebug = "SLEEPING";
        return true;
    }

    private Player nearestVisiblePlayer(Collection<Player> players, double range) {
        Location current = npc.getEntity().getLocation();
        double rangeSquared = range * range;
        return players.stream()
                .filter(player -> !player.isInvisible())
                .filter(player -> current.distanceSquared(player.getLocation()) <= rangeSquared)
                .min(Comparator.comparingDouble(player -> current.distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private boolean hasNearbyDanger(Location location, LivingNpcConfig config) {
        Collection<Monster> monsters = location.getWorld()
                .getNearbyEntitiesByType(Monster.class, location, config.dangerRange());
        return !monsters.isEmpty();
    }

    private boolean isWorkEnabled(CropWork work) {
        return switch (work.type()) {
            case HARVEST -> definition.enabled(BehaviorFlag.HARVEST)
                    && definition.enabled(BehaviorFlag.PLANT);
            case PLANT -> definition.enabled(BehaviorFlag.PLANT);
        };
    }

    private void economyLog(String message) {
        java.util.logging.Logger.getLogger("LivingNPC").info(
                "NPC_STORAGE uuid=" + npc.getUniqueId() + " " + message);
    }

    private String harvestOutput(Material crop) {
        return switch (crop) {
            case WHEAT -> "wheat";
            case CARROTS -> "carrot";
            case POTATOES -> "potato";
            case BEETROOTS -> "beetroot";
            default -> null;
        };
    }

    private Material seedFor(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            default -> Material.WHEAT_SEEDS;
        };
    }

    static int wheatSeedSurplus(int harvestedSeeds) {
        return Math.max(0, harvestedSeeds - 1);
    }

    private boolean startDelivery(long serverTick, LivingNpcConfig config) {
        Location current = npc.getEntity().getLocation();
        if (serverTick < nextNavigationAttemptTick) return true;
        if (deliveryCandidates == null) {
            deliveryCandidates = villageStore.deliveryChests(definition.villageId()).stream()
                    .filter(chest -> current.getWorld().equals(chest.getWorld()))
                    .filter(chest -> Math.abs(chest.getBlockY() - current.getBlockY()) <= 4)
                    .sorted(Comparator.comparingDouble(chest -> current.distanceSquared(chest)))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayDeque::new));
        }
        while (!deliveryCandidates.isEmpty()) {
            Location chest = deliveryCandidates.pollFirst();
            Location standingTarget = findStandingLocation(chest, current);
            if (standingTarget == null) continue;
            activeDeliveryChest = chest.clone();
            clearHand();
            if (navigate(standingTarget, FarmerPhase.GOING_TO_STORAGE, serverTick, config)) return true;
        }
        activeDeliveryChest = null;
        deliveryCandidates = null;
        return false;
    }

    private boolean handleDelivery(long serverTick, LivingNpcConfig config, Location plot) {
        if (phase == FarmerPhase.GOING_TO_STORAGE) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.ARRIVED) {
                Location chest = activeDeliveryChest;
                if (chest != null) {
                    faceBlock(chest);
                    if (npc.getEntity() instanceof LivingEntity living) {
                        living.swingMainHand();
                    }
                    chest.getWorld().playSound(chest, org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
                }
                nextActionTick = serverTick + 30L;
                phase = FarmerPhase.DEPOSITING;
            } else if (result == NavigationResult.FAILED) {
                activeDeliveryChest = null;
                phase = FarmerPhase.FINDING_WORK;
                startDelivery(serverTick, config);
            }
            return true;
        }
        if (phase == FarmerPhase.DEPOSITING) {
            Location chest = activeDeliveryChest;
            if (chest != null) {
                faceBlock(chest);
            }
            if (serverTick >= nextActionTick) {
                boolean deposited = economy.depositCarriedLoot(npc.getUniqueId(), definition.villageId());
                if (!deposited) {
                    economyLog("STORAGE_DEPOSIT_FAILED");
                    phase = FarmerPhase.FINDING_WORK;
                    return true;
                }
                economyLog("STORAGE_DEPOSIT slots=" + economy.carriedSlotCount(npc.getUniqueId()));
                if (chest != null) {
                    chest.getWorld().playSound(chest, org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.0f);
                }
                pendingDelivery = 0;
                activeDeliveryChest = null;
                deliveryCandidates = null;
                Location plotEntry = findPlotEntry(plot, npc.getEntity().getLocation(), definition.plotRadius());
                if (plotEntry == null
                        || !navigate(plotEntry, FarmerPhase.RETURNING_TO_PLOT, serverTick, config)) {
                    phase = FarmerPhase.INACTIVE;
                }
            }
            return true;
        }
        if (phase == FarmerPhase.RETURNING_TO_PLOT) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result != NavigationResult.IN_PROGRESS) {
                if (result == NavigationResult.ARRIVED) {
                    holdHoe();
                    workQueue = null;
                    phase = FarmerPhase.FINDING_WORK;
                } else {
                    phase = FarmerPhase.INACTIVE;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleSocial(long serverTick, LivingNpcConfig config) {
        if (phase != FarmerPhase.GOING_TO_MARKET && phase != FarmerPhase.GOING_TO_SCENIC
                && phase != FarmerPhase.SHOPPING && phase != FarmerPhase.SOCIALIZING) {
            return false;
        }
        Location location = npc.getEntity().getLocation();
        if (hasNearbyDanger(location, config) || location.getWorld().hasStorm()) {
            clearSocial();
            goHome(serverTick, config, FarmerPhase.SHELTERING);
            return true;
        }
        if (phase == FarmerPhase.GOING_TO_MARKET || phase == FarmerPhase.GOING_TO_SCENIC) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.ARRIVED) {
                phase = socialType.equals("cho") ? FarmerPhase.SHOPPING : FarmerPhase.SOCIALIZING;
                nextActionTick = serverTick + randomBetween(100, 200);
            } else if (result == NavigationResult.FAILED) {
                clearSocial();
                phase = FarmerPhase.INACTIVE;
            }
            return true;
        }
        if (!socialSpoken && socialPartner != null) {
            NPC partner = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getByUniqueId(socialPartner);
            if (partner != null && partner.isSpawned()
                    && partner.getEntity().getWorld().equals(location.getWorld())
                    && partner.getEntity().getLocation().distanceSquared(location) <= 36.0) {
                faceHorizontal(partner.getEntity().getLocation());
                speakSocial(partner, location);
                socialSpoken = true;
            }
        }
        if (serverTick >= nextActionTick) {
            clearSocial();
            phase = FarmerPhase.INACTIVE;
        }
        return true;
    }

    private void speakSocial(NPC partner, Location location) {
        long time = location.getWorld().getTime();
        String line;
        if (socialType.equals("cho")) {
            line = time < 12000L
                    ? "Chợ hôm nay khá nhộn nhịp. Bạn đã xem giá nông sản chưa, " + partner.getName() + "?"
                    : "Sắp tối rồi, mình xem nốt quầy hàng rồi về nhé, " + partner.getName() + ".";
        } else {
            line = time < 12000L
                    ? "Từ đây nhìn rõ cả làng. Ruộng hôm nay phát triển tốt đấy, " + partner.getName() + "."
                    : "Trời đang tối dần, ngắm một lát rồi chúng ta về nhé, " + partner.getName() + ".";
        }
        Component message = Component.text(definition.profile().name() + ": " + line, NamedTextColor.GOLD);
        for (Player player : location.getWorld().getNearbyPlayers(location, 20.0)) {
            player.sendMessage(message);
        }
    }

    private void clearSocial() {
        socialPartner = null;
        socialType = null;
        socialSpoken = false;
    }

    private void sellCompletedShift(Location location, LivingNpcConfig config) {
        long fullTime = location.getWorld().getFullTime();
        long completedShift = SchedulePolicy.completedShiftKey(fullTime, schedule(config));
        economy.sellAtShiftEnd(npc.getUniqueId(), definition.villageId(), completedShift);
    }

    void finishFarmerShift(LivingNpcConfig config) {
        if (npc.isSpawned() && config.sellAtShiftEnd() && definition.enabled(BehaviorFlag.SELL_INVENTORY)) {
            sellCompletedShift(npc.getEntity().getLocation(), config);
        }
        observedShiftKey = null;
    }

    private void trackShiftTransition(
            Location location, LivingNpcConfig config, ResidentSchedule schedule, boolean scheduledNow) {
        if (!definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)) {
            observedShiftKey = null;
            return;
        }
        if (scheduledNow) {
            observedShiftKey = SchedulePolicy.activeShiftKey(location.getWorld().getFullTime(), schedule);
        } else if (observedShiftKey != null) {
            if (config.sellAtShiftEnd() && definition.enabled(BehaviorFlag.SELL_INVENTORY)) {
                economy.sellAtShiftEnd(npc.getUniqueId(), definition.villageId(), observedShiftKey);
            }
            observedShiftKey = null;
        }
    }

    private ResidentSchedule schedule(LivingNpcConfig config) {
        return definition.schedule(
                ResidentRole.FARMER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
    }

    private Location findStandingLocation(Location crop, Location current) {
        java.util.ArrayList<Location> candidates = new java.util.ArrayList<>();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            Location candidate = crop.clone().add(offset[0] + 0.5, 0, offset[1] + 0.5);
            Block feet = candidate.getBlock();
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable() || feet.getRelative(0, -1, 0).isPassable()) {
                continue;
            }
            candidates.add(candidate);
        }
        return nearestCandidate(candidates, current);
    }

    private Location findNearestStandingLocation(Location target, Location current) {
        java.util.ArrayList<Location> candidates = new java.util.ArrayList<>();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            Location candidate = target.clone().add(offset[0] + 0.5, 0, offset[1] + 0.5);
            Block feet = candidate.getBlock();
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()
                    || feet.getRelative(0, -1, 0).isPassable()) {
                continue;
            }
            candidates.add(candidate);
        }
        return nearestCandidate(candidates, current);
    }

    static Location nearestCandidate(java.util.List<Location> candidates, Location current) {
        return candidates.stream()
                .filter(candidate -> candidate.getWorld().equals(current.getWorld()))
                .min(java.util.Comparator.comparingDouble(current::distanceSquared))
                .orElse(null);
    }

    static boolean requiresPlotEntry(Location current, Location plot, int radius) {
        if (current == null || plot == null || current.getWorld() == null
                || !current.getWorld().equals(plot.getWorld())) {
            return true;
        }
        int boundedRadius = Math.max(1, radius);
        return Math.abs(current.getBlockX() - plot.getBlockX()) > boundedRadius
                || Math.abs(current.getBlockZ() - plot.getBlockZ()) > boundedRadius
                || Math.abs(current.getBlockY() - plot.getBlockY()) > 2;
    }

    static boolean plotEntryReached(Location current, Location plot, int radius) {
        return !requiresPlotEntry(current, plot, radius);
    }

    private Location findPlotEntry(Location plot, Location current, int radius) {
        int boundedRadius = Math.max(1, radius);
        for (int candidateRadius : new int[] {Math.max(0, boundedRadius - 1), boundedRadius, boundedRadius + 1}) {
            java.util.ArrayList<Location> candidates = new java.util.ArrayList<>();
            for (int x = -candidateRadius; x <= candidateRadius; x++) {
                for (int z = -candidateRadius; z <= candidateRadius; z++) {
                    if (Math.abs(x) != candidateRadius && Math.abs(z) != candidateRadius) continue;
                    for (int y = 2; y >= -2; y--) {
                        Location candidate = plot.clone().add(x + 0.5, y, z + 0.5);
                        Block feet = candidate.getBlock();
                        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()
                                || feet.getRelative(0, -1, 0).isPassable()) continue;
                        candidates.add(candidate);
                    }
                }
            }
            Location entry = nearestCandidate(candidates, current);
            if (entry != null) return entry;
        }
        return null;
    }

    private Location randomWanderTarget(Location center, int radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
            for (int y = center.getBlockY() + 2; y >= center.getBlockY() - 2; y--) {
                Block feet = center.getWorld().getBlockAt(x, y, z);
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && !feet.getRelative(0, -1, 0).isPassable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    private void faceRandomDirection() {
        Location current = npc.getEntity().getLocation();
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        Location eyeLevel = npc.getEntity() instanceof LivingEntity living
                ? living.getEyeLocation()
                : current.clone().add(0, 1.6, 0);
        faceHorizontal(eyeLevel.add(Math.cos(angle) * 4.0, 0, Math.sin(angle) * 4.0));
    }

    private void updateLookPose() {
        if (isSeatingPhase()) {
            seatManager.lockRotation(npc);
            return;
        }
        if ((phase == FarmerPhase.INSPECTING || phase == FarmerPhase.WORKING) && currentWork != null) {
            faceBlock(currentWork.location());
            return;
        }
        if (phase == FarmerPhase.DEPOSITING) {
            Location chest = activeDeliveryChest;
            if (chest != null) {
                faceBlock(chest);
                return;
            }
        }
        faceForwardHorizontal();
    }

    private void faceBlock(Location block) {
        setRotationToward(block.clone().add(0.5, 0.35, 0.5), true);
    }

    private void faceHorizontal(Location target) {
        setRotationToward(target, false);
    }

    private void faceForwardHorizontal() {
        org.bukkit.entity.Entity entity = npc.getEntity();
        entity.setRotation(entity.getYaw(), 0.0f);
    }

    private void setRotationToward(Location target, boolean lookDown) {
        org.bukkit.entity.Entity entity = npc.getEntity();
        Location eye = entity instanceof LivingEntity living
                ? living.getEyeLocation()
                : entity.getLocation().add(0, 1.6, 0);
        double dx = target.getX() - eye.getX();
        double dz = target.getZ() - eye.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = lookDown
                ? lookPitchDegrees(Math.hypot(dx, dz), target.getY() - eye.getY())
                : 0.0f;
        entity.setRotation(yaw, pitch);
    }

    static float lookPitchDegrees(double horizontalDistance, double verticalDelta) {
        double horizontal = Math.max(0.01, horizontalDistance);
        return (float) Math.clamp(-Math.toDegrees(Math.atan2(verticalDelta, horizontal)), -60.0, 60.0);
    }

    private void scheduleAmbient(long serverTick, LivingNpcConfig config) {
        nextAmbientTick = serverTick + randomBetween(config.ambientIntervalMinTicks(), config.ambientIntervalMaxTicks());
    }

    private boolean beginSeating(
            long serverTick, LivingNpcConfig config, SeatType type, FarmerPhase resumePhase) {
        if (serverTick < nextNavigationAttemptTick) return false;
        if (definition.villageId() == null || seatManager.reserveClosest(
                npc.getUniqueId(), definition.villageId(), type, npc.getEntity().getLocation(),
                location -> npc.getNavigator().canNavigateTo(
                        location, LivingNavigation.allowDoors(npc.getNavigator().getLocalParameters()))) == null) {
            nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        Location approach = SeatValidator.approachLocation(seatManager.seat(npc.getUniqueId()));
        if (approach == null || !navigate(approach, FarmerPhase.GOING_TO_SEAT, serverTick, config)) {
            seatManager.release(npc);
            return false;
        }
        seatingType = type;
        seatResumePhase = resumePhase;
        return true;
    }

    private void tickSeating(long serverTick, LivingNpcConfig config) {
        if (phase == FarmerPhase.GOING_TO_SEAT) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.IN_PROGRESS) return;
            if (result != NavigationResult.ARRIVED || !seatManager.sit(npc)) {
                finishSeating();
                return;
            }
            phase = seatingType == SeatType.DINING ? FarmerPhase.SITTING_DINING : FarmerPhase.SITTING_REST;
            if (seatingType == SeatType.DINING) {
                setHand(new ItemStack(Material.BREAD));
                nextEatingAnimationTick = serverTick + 20L;
            }
            seatEndTick = serverTick + randomBetween(
                    (int) config.seating().restDurationMinTicks(),
                    (int) config.seating().restDurationMaxTicks());
            return;
        }
        if (phase == FarmerPhase.SITTING_REST || phase == FarmerPhase.SITTING_DINING) {
            if (!SeatValidator.stillValid(seatManager.seat(npc.getUniqueId()))) {
                startStanding(serverTick, config, seatResumePhase);
                return;
            }
            seatManager.lockRotation(npc);
            if (phase == FarmerPhase.SITTING_DINING && serverTick >= nextEatingAnimationTick
                    && npc.getEntity() instanceof LivingEntity living) {
                living.swingMainHand();
                nextEatingAnimationTick = serverTick + 30L;
            }
            if (serverTick >= seatEndTick) startStanding(serverTick, config, seatResumePhase);
            return;
        }
        if (phase == FarmerPhase.STANDING_UP) {
            if (serverTick < standEndTick) return;
            npc.setSneaking(false);
            FarmerPhase resume = seatResumePhase == null ? FarmerPhase.INACTIVE : seatResumePhase;
            if (seatingType == SeatType.DINING) lunchSeatCompleted = true;
            seatManager.release(npc);
            seatingType = null;
            seatResumePhase = null;
            phase = resume;
            scheduleAmbient(serverTick, config);
        }
    }

    private void startStanding(long serverTick, LivingNpcConfig config, FarmerPhase resumePhase) {
        seatManager.release(npc);
        clearHand();
        npc.setSneaking(true);
        seatResumePhase = resumePhase;
        standEndTick = serverTick + config.seating().standDurationTicks();
        phase = FarmerPhase.STANDING_UP;
    }

    private void finishSeating() {
        FarmerPhase resume = seatResumePhase == null ? FarmerPhase.INACTIVE : seatResumePhase;
        seatManager.release(npc);
        clearHand();
        npc.setSneaking(false);
        if (seatingType == SeatType.DINING) lunchSeatCompleted = true;
        seatingType = null;
        seatResumePhase = null;
        phase = resume;
    }

    private boolean isSeatingPhase() {
        return phase == FarmerPhase.GOING_TO_SEAT
                || phase == FarmerPhase.SITTING_REST
                || phase == FarmerPhase.SITTING_DINING
                || phase == FarmerPhase.STANDING_UP;
    }

    private boolean navigate(Location target, FarmerPhase targetPhase, long serverTick, LivingNpcConfig config) {
        if (!MovementService.valid(targetPhase, npc.getEntity().getLocation(), target)) {
            java.util.logging.Logger.getLogger("LivingNPC").warning(
                    "NPC_MOVE_FAIL uuid=" + npc.getUniqueId() + " intent="
                            + MovementService.intentFor(targetPhase) + " reason=INVALID_TARGET");
            return false;
        }
        if (serverTick < nextNavigationAttemptTick) {
            return false;
        }
        String leaseOwner = navigationOwner(targetPhase);
        int leasePriority = navigationPriority(targetPhase);
        if (!navigationLeases.claim(npc.getUniqueId(), leaseOwner, leasePriority, this::navigationPreempted)) {
            return false;
        }
        navigationLeaseOwner = leaseOwner;
        Navigator navigator = npc.getNavigator();
        navigator.cancelNavigation();
        double navigationMargin = targetPhase == FarmerPhase.GOING_TO_BED
                ? Math.min(config.navigationDistanceMargin(), 1.0)
                : config.navigationDistanceMargin();
        NavigatorParameters parameters = targetPhase == FarmerPhase.GOING_TO_BED
                ? LivingNavigation.enterBuildings(navigator.getLocalParameters())
                : LivingNavigation.allowDoors(navigator.getLocalParameters());
        parameters
                .speedModifier((float) (config.navigationSpeedModifier()
                        * definition.progress(ResidentRole.FARMER).speedMultiplier()))
                .distanceMargin(navigationMargin)
                .pathDistanceMargin(navigationMargin)
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        String operation = targetPhase.name();
        java.util.logging.Logger.getLogger("LivingNPC").info(
                "NPC_MOVE_INTENT uuid=" + npc.getUniqueId() + " intent="
                        + MovementService.intentFor(targetPhase) + " target=" + target.getBlockX() + ","
                        + target.getBlockY() + "," + target.getBlockZ());
        Location current = npc.getEntity().getLocation();
        VillageDefinition village = villageStore.get(definition.villageId());
        List<NavigationGate> configuredGates = village == null
                ? List.of() : village.navigationGates();
        List<GateRoute.Candidate> gates = GateRouteDiscovery.discover(
                current, target, configuredGates, definition.activeRole());
        if (!gates.isEmpty()) {
            if (startGateRoute(navigator, target, gates, operation, serverTick, config, navigationMargin)) {
                navigationTarget = target.clone();
                navigationStartedTick = serverTick;
                phase = targetPhase;
                return true;
            }
            releaseNavigationLease();
            nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        if (targetPhase == FarmerPhase.GOING_TO_PLOT && !configuredGates.isEmpty()) {
            releaseNavigationLease();
            nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        if ((targetPhase == FarmerPhase.GOING_HOME || targetPhase == FarmerPhase.GOING_TO_BED)
                && current.distance(target) > WaypointRoutePlanner.DEFAULT_SEGMENT_BLOCKS) {
            if (startWaypointRoute(navigator, current, target, operation, serverTick, config, parameters, navigationMargin)) {
                navigationTarget = target.clone();
                navigationStartedTick = serverTick;
                phase = targetPhase;
                return true;
            }
            releaseNavigationLease();
            nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        if (!NavigationDiagnostics.shared().targetInRange(current, target, parameters.range())) {
            NavigationDiagnostics.shared().targetOutOfRange(
                npc, parameters, operation, target,
                    navigationMargin, navigationMargin);
            releaseNavigationLease();
            nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        if (!MovementService.startSimpleNavigation(
                navigator, target,
                (float) (config.navigationSpeedModifier()
                        * definition.progress(ResidentRole.FARMER).speedMultiplier()),
                        navigationMargin)) return false;
        NavigatorParameters activeParameters = NavigationDiagnostics.shared()
                .activeParametersAfterTarget(navigator, target, navigationMargin);
        activeParameters
                .speedModifier((float) (config.navigationSpeedModifier()
                        * definition.progress(ResidentRole.FARMER).speedMultiplier()))
                .distanceMargin(navigationMargin)
                .pathDistanceMargin(navigationMargin)
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        LivingNavigation.allowDoors(activeParameters);
        NavigationDiagnostics.shared().attach(
                npc, activeParameters, operation, target,
                navigationMargin, navigationMargin);
        navigationTarget = target.clone();
        navigationStartedTick = serverTick;
        phase = targetPhase;
        return true;
    }

    private boolean startWaypointRoute(
            Navigator navigator, Location current, Location target, String operation,
            long serverTick, LivingNpcConfig config, NavigatorParameters parameters, double navigationMargin) {
        List<Location> waypoints = WaypointRoutePlanner.plan(
                current, target, WaypointRoutePlanner.DEFAULT_SEGMENT_BLOCKS);
        if (!RuntimeChunkAvailability.loadedRoute(waypoints)) {
            NavigationDiagnostics.shared().targetOutOfRange(
                    npc, parameters, operation + "_WAYPOINT", target,
                    navigationMargin, navigationMargin);
            return false;
        }
        waypointRouteCoordinator = new WaypointRouteCoordinator(
                new WaypointRouteCoordinator.Navigation() {
                    @Override public boolean start(Location legTarget) {
                        if (!NavigationDiagnostics.shared().targetInRange(
                                npc.getEntity().getLocation(), legTarget, parameters.range())) return false;
                        if (!MovementService.startSimpleNavigation(
                                navigator, legTarget, config.navigationSpeedModifier(),
                                navigationMargin)) return false;
                        NavigatorParameters activeParameters = NavigationDiagnostics.shared()
                                .activeParametersAfterTarget(
                                        navigator, legTarget, navigationMargin);
                        LivingNavigation.allowDoors(activeParameters)
                                .speedModifier(config.navigationSpeedModifier())
                                .distanceMargin(navigationMargin)
                                .pathDistanceMargin(navigationMargin)
                                .destinationTeleportMargin(0.0)
                                .stuckAction((stuckNpc, stuckNavigator) -> false);
                        NavigationDiagnostics.shared().attach(
                                npc, activeParameters, operation + "_WAYPOINT", legTarget,
                                config.navigationDistanceMargin(), config.navigationDistanceMargin());
                        return true;
                    }
                    @Override public boolean navigating() { return navigator.isNavigating(); }
                    @Override public void cancel() { navigator.cancelNavigation(); }
                    @Override public boolean recover(Location current, Location legTarget, int radius) {
                        NavigationRecovery.Result result = NavigationRecovery.recover(
                                npc, legTarget, radius, org.bukkit.Bukkit.getCurrentTick(),
                                operation + "_WAYPOINT_RECOVERY", config.navigationRetryBackoffTicks());
                        return result == NavigationRecovery.Result.RECOVERED;
                    }
                }, waypoints, navigationMargin, config.navigationTimeoutTicks());
        return waypointRouteCoordinator.start(serverTick)
                == WaypointRouteCoordinator.Result.IN_PROGRESS;
    }

    private static String compactLocation(Location location) {
        if (location == null || location.getWorld() == null) return "null";
        return location.getWorld().getName() + ":"
                + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                        location.getX(), location.getY(), location.getZ());
    }

    private boolean startGateRoute(
            Navigator navigator, Location target, List<GateRoute.Candidate> gates,
            String operation, long serverTick, LivingNpcConfig config, double navigationMargin) {
        java.util.logging.Logger.getLogger("LivingNPC").info(
                "NPC_GATE_PLAN uuid=" + npc.getUniqueId() + " operation=" + operation
                        + " finalTarget=" + compactLocation(target) + " candidates="
                        + gates.stream().limit(4).map(candidate -> candidate.key()
                                + "[approach=" + compactLocation(candidate.approach())
                                + ",exit=" + compactLocation(candidate.exit()) + "]")
                        .collect(java.util.stream.Collectors.joining(";")));
        gateRouteCoordinator = new GateRouteCoordinator(
                new GateRouteCoordinator.Navigation() {
                    @Override
                    public boolean start(Location legTarget, double margin) {
                        return start(legTarget, margin, null);
                    }

                    @Override
                    public boolean start(Location legTarget, double margin, GateRoute.Leg leg) {
                        return start(legTarget, margin, leg, 0L);
                    }

                    @Override
                    public boolean start(
                            Location legTarget, double margin, GateRoute.Leg leg, long generation,
                            GateRoute.Candidate routeCandidate) {
                        NavigatorParameters parameters = navigator.getLocalParameters();
                        Location current = npc.getEntity().getLocation();
                        if (!NavigationDiagnostics.shared().targetInRange(current, legTarget, parameters.range())) {
                            NavigationDiagnostics.shared().targetOutOfRange(
                                    npc, parameters, operation + "_GATE", legTarget,
                                    margin, margin);
                            return false;
                        }
                        // Gate leg cần Citizens đi tới exact target; distance/path margin của Citizens
                        // nếu dùng 0.75 sẽ kết thúc tại block-goal trước ô đứng thật.
                        if (leg == GateRoute.Leg.STAGING || leg == GateRoute.Leg.APPROACH
                                || leg == GateRoute.Leg.EXIT) {
                            if (routeCandidate == null) return false;
                            LivingNavigation.constrainGateRoute(parameters, routeCandidate, leg);
                        } else {
                            LivingNavigation.clearGateRouteConstraint(parameters);
                        }
                        if (!MovementService.startSimpleNavigation(
                                navigator, legTarget, config.navigationSpeedModifier(), 0.0)) return false;
                        NavigatorParameters activeParameters = NavigationDiagnostics.shared()
                                .activeParametersAfterTarget(navigator, legTarget, 0.0);
                        activeParameters
                                .speedModifier(config.navigationSpeedModifier())
                                .distanceMargin(0.0)
                                .pathDistanceMargin(0.0)
                                .destinationTeleportMargin(0.0)
                                .stuckAction((stuckNpc, stuckNavigator) -> false);
                        LivingNavigation.allowDoors(activeParameters);
                        if (leg == GateRoute.Leg.STAGING || leg == GateRoute.Leg.APPROACH
                                || leg == GateRoute.Leg.EXIT) {
                            LivingNavigation.constrainGateRoute(activeParameters, routeCandidate, leg);
                        } else {
                            LivingNavigation.clearGateRouteConstraint(activeParameters);
                        }
                        String legOperation = operation + "_GATE_"
                                + (leg == null ? "UNKNOWN" : leg.name())
                                + "_GEN_" + generation;
                        NavigationDiagnostics.shared().attach(
                                npc, activeParameters, legOperation, legTarget,
                                margin, margin);
                        return true;
                    }

                    @Override
                    public boolean navigating() {
                        return navigator.isNavigating();
                    }

                    @Override
                    public void cancel() {
                        if (navigator.isNavigating()) navigator.cancelNavigation();
                    }

                    @Override
                    public boolean recover(Location current, Location legTarget, int radius) {
                        NavigationRecovery.Result result = NavigationRecovery.recover(
                                npc, legTarget, radius, org.bukkit.Bukkit.getCurrentTick(),
                                operation + "_GATE_RECOVERY", config.navigationRetryBackoffTicks());
                        return result == NavigationRecovery.Result.RECOVERED;
                    }

                    private Location routeTargetForRecovery() {
                        return npc.getEntity().getLocation();
                    }

                    @Override
                    public boolean requestGate(String gateKey) {
                        return GatePassageService.request(npc, gateKey);
                    }

                    @Override
                    public void releaseGate(String gateKey) {
                        GatePassageService.release(npc, gateKey);
                    }
                },
                config.navigationTimeoutTicks(), navigationMargin, 1.0);
        GateRouteCoordinator.Result result = gateRouteCoordinator.start(
                npc.getEntity().getLocation(), target, gates, serverTick);
        if (result == GateRouteCoordinator.Result.IN_PROGRESS) return true;
        gateRouteCoordinator = null;
        return false;
    }

    private NavigationResult navigationResult(long serverTick, LivingNpcConfig config) {
        if (navigationTarget == null || !npc.getEntity().getWorld().equals(navigationTarget.getWorld())) {
            return navigationFailed(serverTick, config);
        }
        if (gateRouteCoordinator != null && gateRouteCoordinator.active()) {
            GateRouteCoordinator.Result gateResult = gateRouteCoordinator.tick(
                    npc.getEntity().getLocation(), serverTick);
            if (gateResult == GateRouteCoordinator.Result.IN_PROGRESS) return NavigationResult.IN_PROGRESS;
            gateRouteCoordinator = null;
            if (gateResult == GateRouteCoordinator.Result.COMPLETE) {
                navigationTarget = null;
                navigationStage = null;
                releaseNavigationLease();
                return NavigationResult.ARRIVED;
            }
            return navigationFailed(serverTick, config);
        }
        if (waypointRouteCoordinator != null) {
            WaypointRouteCoordinator.Result waypointResult = waypointRouteCoordinator.tick(
                    npc.getEntity().getLocation(), serverTick);
            if (waypointResult == WaypointRouteCoordinator.Result.IN_PROGRESS) {
                return NavigationResult.IN_PROGRESS;
            }
            waypointRouteCoordinator = null;
            if (waypointResult == WaypointRouteCoordinator.Result.COMPLETE) {
                navigationTarget = null;
                navigationStage = null;
                releaseNavigationLease();
                return NavigationResult.ARRIVED;
            }
            return navigationFailed(serverTick, config);
        }
        double margin = phase == FarmerPhase.GOING_TO_BED
                ? Math.min(config.navigationDistanceMargin(), 1.0)
                : config.navigationDistanceMargin();
        if (navigationTargetReached(npc.getEntity().getLocation(), navigationTarget, margin)) {
            navigationTarget = null;
            navigationStage = null;
            releaseNavigationLease();
            return NavigationResult.ARRIVED;
        }

        if (serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
            npc.getNavigator().cancelNavigation();
            NavigationRecovery.Result recovery = NavigationRecovery.recover(
                    npc, navigationTarget, 2, serverTick, phase.name(), config.navigationRetryBackoffTicks());
            if (recovery == NavigationRecovery.Result.RECOVERED) {
                nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
            }
            return navigationFailed(serverTick, config);
        }
        if (!npc.getNavigator().isNavigating()) {
            return navigationFailed(serverTick, config);
        }
        return NavigationResult.IN_PROGRESS;
    }

    static boolean navigationTargetReached(Location current, Location target, double margin) {
        if (current == null || target == null || current.getWorld() == null
                || !current.getWorld().equals(target.getWorld())
                || !Double.isFinite(margin) || margin < 0.0) {
            return false;
        }
        double deltaX = current.getX() - target.getBlockX();
        double deltaZ = current.getZ() - target.getBlockZ();
        return deltaX * deltaX + deltaZ * deltaZ <= margin * margin
                && Math.abs(current.getY() - target.getBlockY()) < 1.0;
    }

    private NavigationResult navigationFailed(long serverTick, LivingNpcConfig config) {
        if (gateRouteCoordinator != null) gateRouteCoordinator.cancel();
        gateRouteCoordinator = null;
        if (waypointRouteCoordinator != null) waypointRouteCoordinator.cancel();
        waypointRouteCoordinator = null;
        navigationTarget = null;
        navigationStage = null;
        releaseNavigationLease();
        activeDeliveryChest = null;
        nextNavigationAttemptTick = serverTick + config.navigationRetryBackoffTicks();
        return NavigationResult.FAILED;
    }


    private int randomDelay(LivingNpcConfig config) {
        return scaledDelay(randomBetween(config.actionDelayMinTicks(), config.actionDelayMaxTicks()));
    }

    private int scaledDelay(long ticks) {
        return Math.max(1, (int) Math.ceil(ticks / definition.progress(ResidentRole.FARMER).speedMultiplier()));
    }

    private int randomBetween(int minimum, int maximum) {
        return ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }

    void suspend() {
        if (gateRouteCoordinator != null) gateRouteCoordinator.cancel();
        gateRouteCoordinator = null;
        if (npc.isSpawned() && npc.getEntity() instanceof HumanEntity human && human.isSleeping()) {
            human.wakeup(false);
        }
        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
        seatManager.release(npc);
        activityPointManager.release(npc.getUniqueId());
        releaseNavigationLease();
        npc.setSneaking(false);
        seatingType = null;
        seatResumePhase = null;
        clearHand();
        stopInspection();
        reset();
        sleepingBed = null;
        sleepActive = false;
        morningExitRequired = false;
        morningExitActiveTicks = 0L;
    }

    private void stopInspection() {
        if (inspecting) {
            npc.setSneaking(false);
            inspecting = false;
        }
    }

    private void clearHand() {
        setHand(null);
    }

    private void holdHoe() {
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        ItemStack held = equipment.get(EquipmentSlot.HAND);
        if (held == null || held.getType() != Material.IRON_HOE) {
            setHand(new ItemStack(Material.IRON_HOE));
        }
    }

    private void setHand(ItemStack item) {
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        if (equipment != null) {
            equipment.set(EquipmentSlot.HAND, item);
        }
        if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity living
                && living.getEquipment() != null) {
            living.getEquipment().setItemInMainHand(item);
        }
    }

    private void reset() {
        phase = FarmerPhase.INACTIVE;
        workQueue = null;
        currentWork = null;
        nextAmbientTick = 0L;
        navigationTarget = null;
        navigationStage = null;
        releaseNavigationLease();
        clearSocial();
    }

    private String navigationOwner(FarmerPhase targetPhase) {
        if (targetPhase == FarmerPhase.GOING_TO_BED) return "sleep";
        if (targetPhase == FarmerPhase.LEAVING_HOME) return "morning-exit";
        return "resident-role";
    }

    private int navigationPriority(FarmerPhase targetPhase) {
        if (targetPhase == FarmerPhase.GOING_TO_BED) return 80;
        if (targetPhase == FarmerPhase.LEAVING_HOME) return 70;
        return 30;
    }

    private void navigationPreempted() {
        if (gateRouteCoordinator != null) gateRouteCoordinator.cancel();
        gateRouteCoordinator = null;
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        navigationTarget = null;
        navigationStage = null;
        navigationLeaseOwner = null;
    }

    private void releaseNavigationLease() {
        if (navigationLeaseOwner == null) return;
        navigationLeases.release(npc.getUniqueId(), navigationLeaseOwner);
        navigationLeaseOwner = null;
    }

    void observeGateOpened(String gateKey) {
        if (gateRouteCoordinator != null) gateRouteCoordinator.gateOpenIntent(gateKey);
    }

    private record BedLookup(Location location, String debug) {
    }
}
