package vn.heomc.livingnpc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
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
    private boolean inspecting;
    private int pendingDelivery;
    private UUID socialPartner;
    private String socialType;
    private boolean socialSpoken;

    FarmerRuntime(
            NPC npc,
            FarmerDefinition definition,
            NpcEconomy economy,
            WorldMutationPolicy mutationPolicy,
            VillageStore villageStore,
            java.util.function.LongConsumer experienceAwarder) {
        this.npc = npc;
        this.definition = definition;
        this.economy = economy;
        this.mutationPolicy = mutationPolicy;
        this.villageStore = villageStore;
        this.experienceAwarder = experienceAwarder;
    }

    FarmerRuntime(
            NPC npc, FarmerDefinition definition, NpcEconomy economy,
            WorldMutationPolicy mutationPolicy, VillageStore villageStore) {
        this(npc, definition, economy, mutationPolicy, villageStore, ignored -> { });
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

    boolean availableForSocial(LivingNpcConfig config) {
        if (!npc.isSpawned() || definition.villageId() == null || !definition.enabled(BehaviorFlag.MASTER)) {
            return false;
        }
        Location location = npc.getEntity().getLocation();
        boolean offShift = definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isWorkTime(location.getWorld().getTime(), location.getWorld().hasStorm(), schedule(config));
        return offShift && !hasNearbyDanger(location, config)
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
        navigate(target, type.equals("cho") ? FarmerPhase.GOING_TO_MARKET : FarmerPhase.GOING_TO_SCENIC,
                serverTick, config);
        return true;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        if (!npc.isSpawned()) {
            return;
        }
        if (!definition.enabled(BehaviorFlag.MASTER)) {
            suspend();
            return;
        }
        if (definition.activeRole() != ResidentRole.FARMER) {
            suspend();
            return;
        }
        Location npcLocation = npc.getEntity().getLocation();
        Collection<Player> nearbyPlayers = npcLocation.getWorld().getNearbyPlayers(npcLocation, config.activationRange());
        boolean active = !nearbyPlayers.isEmpty();
        if (!active) {
            suspend();
            return;
        }

        if (definition.enabled(BehaviorFlag.AVOID_MONSTERS) && hasNearbyDanger(npcLocation, config)) {
            goHome(serverTick, config, FarmerPhase.SHELTERING);
            return;
        }

        ResidentSchedule schedule = definition.schedule(
                ResidentRole.FARMER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        boolean workTime = definition.plot() != null
                && definition.villageId() != null
                && villageStore.deliveryChest(definition.villageId()) != null
                && (!definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                        || SchedulePolicy.isWorkTime(
                                npcLocation.getWorld().getTime(),
                                npcLocation.getWorld().hasStorm(),
                                schedule));
        if (!workTime) {
            if (config.sellAtShiftEnd() && definition.enabled(BehaviorFlag.SELL_INVENTORY)) {
                sellCompletedShift(npcLocation, config);
            }
            if (!handleSocial(serverTick, config)) {
                idleAtHome(serverTick, config, nearbyPlayers);
            }
            return;
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
            navigate(plot, FarmerPhase.GOING_TO_PLOT, serverTick, config);
            return;
        }
        if (phase == FarmerPhase.GOING_TO_PLOT) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.ARRIVED) {
                phase = FarmerPhase.FINDING_WORK;
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
        stopInspection();
        clearHand();
        Location home = definition.home().resolve();
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
        } else if (phase == travelPhase) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result != NavigationResult.IN_PROGRESS) {
                clearHand();
                phase = FarmerPhase.INACTIVE;
            }
        } else if (phase != travelPhase) {
            clearHand();
            reset();
        }
    }

    private void idleAtHome(
            long serverTick, LivingNpcConfig config, Collection<Player> nearbyPlayers) {
        Location home = definition.home().resolve();
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
            phase = FarmerPhase.INACTIVE;
        }
        if (isAmbientPhase()) {
            finishAmbientWhenReady(serverTick);
            return;
        }
        if (serverTick >= nextAmbientTick) {
            startAmbient(serverTick, config, home, nearbyPlayers);
        }
    }

    private void findWork(long serverTick, LivingNpcConfig config, Location plot, Collection<Player> nearbyPlayers) {
        if (workQueue == null || workQueue.isEmpty()) {
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
            currentWork = workQueue.pollFirst();
        } while (currentWork != null && !isWorkEnabled(currentWork));
        if (currentWork == null) {
            scheduleAmbient(serverTick, config);
            return;
        }
        Location standingTarget = findStandingLocation(currentWork.location(), npc.getEntity().getLocation());
        if (standingTarget == null) {
            currentWork = null;
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        navigate(standingTarget, FarmerPhase.GOING_TO_CROP, serverTick, config);
    }

    private void inspectWork(long serverTick, LivingNpcConfig config) {
        if (currentWork == null) {
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        npc.faceLocation(currentWork.location().clone().add(0.5, 0.5, 0.5));
        npc.setSneaking(true);
        inspecting = true;
        nextActionTick = serverTick + scaledDelay(config.inspectionDurationTicks());
        phase = FarmerPhase.INSPECTING;
    }

    private void prepareWork(long serverTick, LivingNpcConfig config) {
        npc.setSneaking(false);
        inspecting = false;
        Material held = currentWork.type() == CropWork.Type.PLANT ? seedFor(currentWork.crop()) : Material.IRON_HOE;
        npc.getOrAddTrait(Equipment.class).set(EquipmentSlot.HAND, new ItemStack(held));
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
        if (currentWork.type() == CropWork.Type.HARVEST
                && !economy.canAcceptProduction(
                        npc.getUniqueId(), definition.villageId(), config.outputPerAction(), shiftKey)) {
            currentWork = null;
            clearHand();
            phase = FarmerPhase.FINDING_WORK;
            return;
        }
        if (npc.getEntity() instanceof LivingEntity living) {
            living.swingMainHand();
        }
        boolean actionSucceeded = false;
        boolean produced = false;
        if (currentWork.type() == CropWork.Type.HARVEST
                && CropScanner.isAllowedCrop(block.getType())
                && block.getType() == currentWork.crop()
                && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() == ageable.getMaximumAge()
                && mutationPolicy.allows(block.getLocation(), MutationType.PLACE)) {
            block.setType(Material.AIR, true);
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
                boolean accepted = economy.addProduction(
                        npc.getUniqueId(), definition.villageId(), output, config.outputPerAction(), shiftKey);
                if (accepted) {
                    pendingDelivery += config.outputPerAction();
                }
            }
        }
        if (actionSucceeded) {
            experienceAwarder.accept(10L);
        }
        if (produced) {
            Material harvestedCrop = currentWork.crop();
            currentWork = new CropWork(block.getLocation(), CropWork.Type.PLANT, harvestedCrop);
            npc.getOrAddTrait(Equipment.class).set(
                    EquipmentSlot.HAND, new ItemStack(seedFor(harvestedCrop)));
            nextActionTick = serverTick + Math.max(10L, scaledDelay(config.inspectionDurationTicks()));
            phase = FarmerPhase.WORKING;
            return;
        }
        currentWork = null;
        clearHand();
        scheduleAmbient(serverTick, config);
        if (pendingDelivery > 0 && startDelivery(serverTick, config)) {
            return;
        }
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
                npc.faceLocation(player.getEyeLocation());
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
        return !location.getWorld().getNearbyEntitiesByType(Monster.class, location, config.dangerRange()).isEmpty();
    }

    private boolean isWorkEnabled(CropWork work) {
        return switch (work.type()) {
            case HARVEST -> definition.enabled(BehaviorFlag.HARVEST)
                    && definition.enabled(BehaviorFlag.PLANT);
            case PLANT -> definition.enabled(BehaviorFlag.PLANT);
        };
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

    private boolean startDelivery(long serverTick, LivingNpcConfig config) {
        Location chest = villageStore.deliveryChest(definition.villageId());
        if (chest == null || !npc.getEntity().getWorld().equals(chest.getWorld())) {
            return false;
        }
        Location standingTarget = findStandingLocation(chest, npc.getEntity().getLocation());
        if (standingTarget == null) {
            return false;
        }
        navigate(standingTarget, FarmerPhase.GOING_TO_STORAGE, serverTick, config);
        return true;
    }

    private boolean handleDelivery(long serverTick, LivingNpcConfig config, Location plot) {
        if (phase == FarmerPhase.GOING_TO_STORAGE) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result == NavigationResult.ARRIVED) {
                Location chest = villageStore.deliveryChest(definition.villageId());
                if (chest != null) {
                    npc.faceLocation(chest.clone().add(0.5, 0.5, 0.5));
                    if (npc.getEntity() instanceof LivingEntity living) {
                        living.swingMainHand();
                    }
                    chest.getWorld().playSound(chest, org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
                }
                nextActionTick = serverTick + 30L;
                phase = FarmerPhase.DEPOSITING;
            } else if (result == NavigationResult.FAILED) {
                phase = FarmerPhase.FINDING_WORK;
            }
            return true;
        }
        if (phase == FarmerPhase.DEPOSITING) {
            if (serverTick >= nextActionTick) {
                Location chest = villageStore.deliveryChest(definition.villageId());
                if (chest != null) {
                    chest.getWorld().playSound(chest, org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.0f);
                }
                pendingDelivery = 0;
                navigate(plot, FarmerPhase.RETURNING_TO_PLOT, serverTick, config);
            }
            return true;
        }
        if (phase == FarmerPhase.RETURNING_TO_PLOT) {
            NavigationResult result = navigationResult(serverTick, config);
            if (result != NavigationResult.IN_PROGRESS) {
                phase = result == NavigationResult.ARRIVED ? FarmerPhase.FINDING_WORK : FarmerPhase.INACTIVE;
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
                npc.faceLocation(partner.getEntity().getLocation());
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

    private ResidentSchedule schedule(LivingNpcConfig config) {
        return definition.schedule(
                ResidentRole.FARMER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
    }

    private Location findStandingLocation(Location crop, Location current) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            Location candidate = crop.clone().add(offset[0] + 0.5, 0, offset[1] + 0.5);
            Block feet = candidate.getBlock();
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable() || feet.getRelative(0, -1, 0).isPassable()) {
                continue;
            }
            double distance = current.distanceSquared(candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
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
        npc.faceLocation(eyeLevel.add(Math.cos(angle) * 4.0, 0, Math.sin(angle) * 4.0));
    }

    private void scheduleAmbient(long serverTick, LivingNpcConfig config) {
        nextAmbientTick = serverTick + randomBetween(config.ambientIntervalMinTicks(), config.ambientIntervalMaxTicks());
    }

    private void navigate(Location target, FarmerPhase targetPhase, long serverTick, LivingNpcConfig config) {
        if (serverTick < nextNavigationAttemptTick) {
            return;
        }
        Navigator navigator = npc.getNavigator();
        navigator.cancelNavigation();
        navigator.setTarget(target);
        navigator.getLocalParameters()
                .speedModifier((float) (config.navigationSpeedModifier()
                        * definition.progress(ResidentRole.FARMER).speedMultiplier()))
                .distanceMargin(config.navigationDistanceMargin())
                .pathDistanceMargin(config.navigationDistanceMargin());
        navigationTarget = target.clone();
        navigationStartedTick = serverTick;
        phase = targetPhase;
    }

    private NavigationResult navigationResult(long serverTick, LivingNpcConfig config) {
        if (navigationTarget == null || !npc.getEntity().getWorld().equals(navigationTarget.getWorld())) {
            return navigationFailed(serverTick, config);
        }
        double margin = config.navigationDistanceMargin();
        if (npc.getEntity().getLocation().distanceSquared(navigationTarget) <= margin * margin) {
            navigationTarget = null;
            return NavigationResult.ARRIVED;
        }
        if (serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
            npc.getNavigator().cancelNavigation();
            return navigationFailed(serverTick, config);
        }
        if (!npc.getNavigator().isNavigating()) {
            return navigationFailed(serverTick, config);
        }
        return NavigationResult.IN_PROGRESS;
    }

    private NavigationResult navigationFailed(long serverTick, LivingNpcConfig config) {
        navigationTarget = null;
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
        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
        clearHand();
        stopInspection();
        reset();
    }

    private void stopInspection() {
        if (inspecting) {
            npc.setSneaking(false);
            inspecting = false;
        }
    }

    private void clearHand() {
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        if (equipment.get(EquipmentSlot.HAND) != null) {
            equipment.set(EquipmentSlot.HAND, null);
        }
    }

    private void reset() {
        phase = FarmerPhase.INACTIVE;
        workQueue = null;
        currentWork = null;
        nextAmbientTick = 0L;
        navigationTarget = null;
        clearSocial();
    }
}
