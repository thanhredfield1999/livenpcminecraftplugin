package vn.heomc.livingnpc;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.inventory.ItemStack;

final class CivilProfessionRuntime {
    private static final int PRODUCTION_LIMIT = 12;
    private final NPC npc;
    private final NpcEconomy economy;
    private final VillageStore villages;
    private final WorldMutationPolicy mutationPolicy;
    private final java.util.function.LongConsumer experienceAwarder;
    private FarmerDefinition definition;
    private FarmerPhase phase = FarmerPhase.INACTIVE;
    private Location station;
    private long navigationStartedTick;
    private long nextActionTick;
    private long shiftKey = Long.MIN_VALUE;
    private Block miningBlock;
    private long miningStartedTick;
    private long nextMiningSwingTick;

    CivilProfessionRuntime(
            NPC npc, FarmerDefinition definition, NpcEconomy economy, VillageStore villages,
            WorldMutationPolicy mutationPolicy, java.util.function.LongConsumer experienceAwarder) {
        this.npc = npc;
        this.definition = definition;
        this.economy = economy;
        this.villages = villages;
        this.mutationPolicy = mutationPolicy;
        this.experienceAwarder = experienceAwarder;
    }

    void updateDefinition(FarmerDefinition updated) {
        boolean workAssignmentChanged = workAssignmentChanged(definition, updated);
        definition = updated;
        if (workAssignmentChanged) suspend();
    }

    static boolean workAssignmentChanged(FarmerDefinition previous, FarmerDefinition updated) {
        return previous.activeRole() != updated.activeRole()
                || !Objects.equals(previous.villageId(), updated.villageId());
    }

    static boolean ownsRole(ResidentRole role) {
        return zoneFor(role) != null;
    }

    FarmerPhase phase() {
        return phase;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        ResidentRole role = definition.activeRole();
        VillageWorkZoneType zoneType = zoneFor(role);
        if (!npc.isSpawned() || zoneType == null || !definition.enabled(BehaviorFlag.MASTER)) {
            suspend();
            return;
        }
        VillageDefinition village = villages.get(definition.villageId());
        StoredLocation stored = village == null ? null : village.workZone(zoneType);
        Location center = stored == null ? null : stored.resolve();
        if (center == null || !npc.getEntity().getWorld().equals(center.getWorld())
                || center.getWorld().getNearbyPlayers(center, config.activationRange()).isEmpty()) {
            suspend();
            return;
        }
        if (role == ResidentRole.MINER && !mutationPolicy.hasMiningRegion(center)) {
            suspend();
            return;
        }
        ResidentSchedule schedule = definition.schedule(
                role, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isWorkTime(center.getWorld().getTime(), center.getWorld().hasStorm(), schedule)
                || !WorkZoneValidator.validate(center, zoneType,
                        config.workZoneValidationRadius(), config.workZoneValidationVerticalRange()).valid()) {
            suspend();
            return;
        }
        shiftKey = SchedulePolicy.activeShiftKey(center.getWorld().getFullTime(), schedule);
        if (role == ResidentRole.SECURITY) {
            tickSecurity(center, serverTick, config, village.id());
            return;
        }
        if (role == ResidentRole.MINER) {
            tickMiner(center, village.id(), serverTick, config);
            return;
        }
        if (phase == FarmerPhase.INACTIVE || phase == FarmerPhase.RESTING) {
            if (serverTick < nextActionTick) return;
            station = safeStandingNear(center, npc.getEntity().getLocation());
            if (station == null) {
                nextActionTick = serverTick + config.navigationRetryBackoffTicks();
                return;
            }
            if (npc.getEntity().getLocation().distanceSquared(station)
                    <= config.navigationDistanceMargin() * config.navigationDistanceMargin()) {
                startProducing(role, serverTick);
            } else {
                navigate(station, serverTick, config);
            }
            return;
        }
        if (phase == FarmerPhase.GOING_TO_WORK_STATION) {
            if (npc.getEntity().getLocation().distanceSquared(station)
                    <= config.navigationDistanceMargin() * config.navigationDistanceMargin()) {
                startProducing(role, serverTick);
            } else if (!npc.getNavigator().isNavigating()
                    || serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
                suspend();
                nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            }
            return;
        }
        if (phase == FarmerPhase.PRODUCING && serverTick >= nextActionTick) {
            finishProduction(role, village.id(), serverTick);
        }
    }

    void suspend() {
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        releaseWorkState();
    }

    void releaseWorkState() {
        clearMiningDamage();
        clearHand();
        phase = FarmerPhase.INACTIVE;
        station = null;
        miningBlock = null;
        miningStartedTick = 0L;
        nextMiningSwingTick = 0L;
    }

    static VillageWorkZoneType zoneFor(ResidentRole role) {
        return switch (role) {
            case COOK -> VillageWorkZoneType.COOKING;
            case CRAFTER -> VillageWorkZoneType.CRAFTING;
            case MINER -> VillageWorkZoneType.MINING;
            case SECURITY -> VillageWorkZoneType.SECURITY;
            default -> null;
        };
    }

    private void startProducing(ResidentRole role, long serverTick) {
        setHand(new ItemStack(switch (role) {
            case COOK -> Material.WOODEN_SHOVEL;
            case CRAFTER -> Material.IRON_AXE;
            case MINER -> Material.IRON_PICKAXE;
            default -> Material.AIR;
        }));
        if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
        phase = FarmerPhase.PRODUCING;
        nextActionTick = serverTick + 60L;
    }

    private void finishProduction(ResidentRole role, String villageId, long serverTick) {
        Recipe recipe = chooseRecipe(role, villageId);
        boolean produced = recipe != null && (recipe.inputs().isEmpty()
                ? economy.addRoleProduction(
                        npc.getUniqueId(), villageId, role, recipe.output(), recipe.amount(),
                        PRODUCTION_LIMIT, shiftKey)
                : economy.transformVillageItems(
                        npc.getUniqueId(), villageId, role, recipe.inputs(), recipe.output(),
                        recipe.amount(), PRODUCTION_LIMIT, shiftKey));
        if (produced) {
            experienceAwarder.accept(10L);
            economy.recordActivity(
                    npc.getUniqueId(), villageId, role, recipe.action(), recipe.output(), recipe.amount());
        }
        clearHand();
        phase = FarmerPhase.RESTING;
        nextActionTick = serverTick + (produced ? 100L : 200L);
    }

    private void tickMiner(Location center, String villageId, long serverTick, LivingNpcConfig config) {
        if (phase == FarmerPhase.PRODUCING && miningBlock != null) {
            face(miningBlock.getLocation().add(0.5, 0.5, 0.5));
            if (serverTick >= nextMiningSwingTick) {
                if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
                float progress = Math.clamp(
                        (float) (serverTick - miningStartedTick) / config.miner().breakDelayTicks(), 0.0f, 0.99f);
                sendMiningDamage(progress);
                nextMiningSwingTick = serverTick + config.miner().swingIntervalTicks();
            }
            if (serverTick < nextActionTick) return;
            breakMiningBlock(villageId, serverTick, config);
            return;
        }
        if (phase == FarmerPhase.GOING_TO_WORK_STATION && miningBlock != null) {
            if (station != null && npc.getEntity().getLocation().distanceSquared(station) <= 1.0) {
                setHand(new ItemStack(Material.WOODEN_PICKAXE));
                face(miningBlock.getLocation().add(0.5, 0.5, 0.5));
                if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
                phase = FarmerPhase.PRODUCING;
                miningStartedTick = serverTick;
                nextMiningSwingTick = serverTick + config.miner().swingIntervalTicks();
                nextActionTick = serverTick + config.miner().breakDelayTicks();
            } else if (!npc.getNavigator().isNavigating()
                    || serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
                releaseWorkState();
                nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            }
            return;
        }
        if (serverTick < nextActionTick) return;
        miningBlock = findMiningBlock(center, villages.get(villageId), config.miner());
        if (miningBlock == null) {
            phase = FarmerPhase.RESTING;
            nextActionTick = serverTick + config.miner().scanIntervalTicks();
            return;
        }
        station = safeStandingNear(miningBlock.getLocation(), npc.getEntity().getLocation());
        if (station == null) {
            miningBlock = null;
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return;
        }
        navigate(station, serverTick, config);
    }

    private Block findMiningBlock(Location center, VillageDefinition village, MinerSettings settings) {
        Location current = npc.getEntity().getLocation();
        java.util.List<Block> protectedBlocks = findProtectedBlocks(center, village, settings);
        java.util.List<MiningCandidate> candidates = new java.util.ArrayList<>();
        for (int x = -settings.searchRadius(); x <= settings.searchRadius(); x++) {
            for (int z = -settings.searchRadius(); z <= settings.searchRadius(); z++) {
                int blockX = center.getBlockX() + x;
                int blockZ = center.getBlockZ() + z;
                if (!center.getWorld().isChunkLoaded(blockX >> 4, blockZ >> 4)) continue;
                for (int y = -settings.verticalRange(); y <= settings.verticalRange(); y++) {
                    Block block = center.getWorld().getBlockAt(
                            blockX, center.getBlockY() + y, blockZ);
                    if (!miningOutput(block.getType()).isPresent()
                            || nearProtectedFeature(block, protectedBlocks, settings.avoidanceRadius())
                            || !mutationPolicy.inSameMiningRegion(center, block.getLocation())
                            || !mutationPolicy.allows(block.getLocation(), MutationType.BREAK)) continue;
                    Location standing = safeStandingNear(block.getLocation(), current);
                    if (standing == null || nearProtectedFeature(
                            standing.getBlock(), protectedBlocks, settings.avoidanceRadius())) continue;
                    candidates.add(new MiningCandidate(block, current.distanceSquared(standing)));
                }
            }
        }
        if (candidates.isEmpty()) return null;
        double minimumDistanceSquared = settings.minimumTravelDistance() * settings.minimumTravelDistance();
        java.util.List<MiningCandidate> roaming = candidates.stream()
                .filter(candidate -> candidate.distanceSquared() >= minimumDistanceSquared)
                .toList();
        java.util.List<MiningCandidate> pool = roaming.isEmpty() ? candidates : roaming;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size())).block();
    }

    private java.util.List<Block> findProtectedBlocks(
            Location center, VillageDefinition village, MinerSettings settings) {
        java.util.List<Block> protectedBlocks = new java.util.ArrayList<>();
        int horizontal = settings.searchRadius() + settings.avoidanceRadius();
        int vertical = settings.verticalRange() + settings.avoidanceRadius();
        for (int x = -horizontal; x <= horizontal; x++) for (int z = -horizontal; z <= horizontal; z++) {
            int blockX = center.getBlockX() + x;
            int blockZ = center.getBlockZ() + z;
            if (!center.getWorld().isChunkLoaded(blockX >> 4, blockZ >> 4)) continue;
            for (int y = -vertical; y <= vertical; y++) {
                Block block = center.getWorld().getBlockAt(blockX, center.getBlockY() + y, blockZ);
                if (isProtectedFeature(block.getType())) protectedBlocks.add(block);
            }
        }
        addProtectedLocation(protectedBlocks, definition.home(), center);
        if (village != null) {
            village.deliveryLocations().forEach(location -> addProtectedLocation(protectedBlocks, location, center));
            village.seats().forEach(seat -> addProtectedLocation(protectedBlocks, seat.location(), center));
        }
        return protectedBlocks;
    }

    private void addProtectedLocation(java.util.List<Block> blocks, StoredLocation stored, Location center) {
        Location location = stored == null ? null : stored.resolve();
        if (location != null && location.getWorld().equals(center.getWorld())) blocks.add(location.getBlock());
    }

    private boolean nearProtectedFeature(Block candidate, java.util.List<Block> protectedBlocks, int radius) {
        return protectedBlocks.stream().anyMatch(protectedBlock ->
                Math.abs(candidate.getX() - protectedBlock.getX()) <= radius
                        && Math.abs(candidate.getY() - protectedBlock.getY()) <= radius
                        && Math.abs(candidate.getZ() - protectedBlock.getZ()) <= radius);
    }

    static boolean isProtectedFeature(Material material) {
        if (material.name().endsWith("_BED")) return true;
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL,
                    CRAFTING_TABLE, CARTOGRAPHY_TABLE, FLETCHING_TABLE, SMITHING_TABLE,
                    STONECUTTER, LOOM, GRINDSTONE,
                    FURNACE, BLAST_FURNACE, SMOKER,
                    ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                    BREWING_STAND, ENCHANTING_TABLE, LECTERN,
                    CAMPFIRE, SOUL_CAMPFIRE -> true;
            default -> false;
        };
    }

    private void breakMiningBlock(String villageId, long serverTick, LivingNpcConfig config) {
        Block block = miningBlock;
        if (block == null || !mutationPolicy.allows(block.getLocation(), MutationType.BREAK)) {
            releaseWorkState();
            return;
        }
        java.util.Optional<String> output = miningOutput(block.getType());
        if (output.isEmpty() || !economy.addRoleProduction(
                npc.getUniqueId(), villageId, ResidentRole.MINER, output.get(), 1,
                PRODUCTION_LIMIT, shiftKey)) {
            releaseWorkState();
            nextActionTick = serverTick + config.miner().scanIntervalTicks();
            return;
        }
        face(block.getLocation().add(0.5, 0.5, 0.5));
        if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
        clearMiningDamage();
        experienceAwarder.accept(10L);
        economy.recordActivity(npc.getUniqueId(), villageId, ResidentRole.MINER, "Khai thác", output.get(), 1);
        clearHand();
        miningBlock = null;
        station = null;
        phase = FarmerPhase.RESTING;
        nextActionTick = serverTick + config.miner().scanIntervalTicks();
    }

    private java.util.Optional<String> miningOutput(Material material) {
        return java.util.Optional.ofNullable(switch (material) {
            case STONE, DEEPSLATE, COBBLESTONE, COBBLED_DEEPSLATE -> "cobblestone";
            case COAL_ORE, DEEPSLATE_COAL_ORE -> "coal";
            case IRON_ORE, DEEPSLATE_IRON_ORE -> "raw_iron";
            default -> null;
        });
    }

    private Recipe chooseRecipe(ResidentRole role, String villageId) {
        NpcAccount town = economy.villageAccount(villageId);
        if (role == ResidentRole.COOK) {
            for (Recipe recipe : List.of(
                    new Recipe(Map.of("chicken", 1), "cooked_chicken", 1, "Nấu thức ăn"),
                    new Recipe(Map.of("cod", 1), "cooked_cod", 1, "Nấu thức ăn"),
                    new Recipe(Map.of("salmon", 1), "cooked_salmon", 1, "Nấu thức ăn"))) {
                if (town.quantity(recipe.inputs().keySet().iterator().next()) > 0) return recipe;
            }
        } else if (role == ResidentRole.CRAFTER) {
            if (town.quantity("raw_iron") > 0) return new Recipe(Map.of("raw_iron", 1), "iron_ingot", 1, "Luyện sắt");
            if (town.quantity("iron_ingot") >= 2) return new Recipe(Map.of("iron_ingot", 2), "shears", 1, "Chế tạo kéo");
            if (town.quantity("wheat") >= 2) return new Recipe(Map.of("wheat", 2), "bread", 1, "Làm bánh mì");
        } else if (role == ResidentRole.MINER) {
            int roll = ThreadLocalRandom.current().nextInt(10);
            return roll < 6
                    ? new Recipe(Map.of(), "cobblestone", 1, "Khai thác")
                    : roll < 9 ? new Recipe(Map.of(), "coal", 1, "Khai thác")
                    : new Recipe(Map.of(), "raw_iron", 1, "Khai thác");
        }
        return null;
    }

    private void tickSecurity(Location center, long serverTick, LivingNpcConfig config, String villageId) {
        Monster danger = center.getWorld().getNearbyEntitiesByType(Monster.class, center, 12.0).stream()
                .min(java.util.Comparator.comparingDouble(monster ->
                        npc.getEntity().getLocation().distanceSquared(monster.getLocation()))).orElse(null);
        if (danger != null) {
            setHand(new ItemStack(Material.SHIELD));
            face(danger.getLocation());
            if (phase != FarmerPhase.ALERTING || serverTick >= nextActionTick) {
                economy.recordActivity(npc.getUniqueId(), villageId, ResidentRole.SECURITY,
                        "Phát hiện quái vật", danger.getType().getKey().getKey(), 1);
                experienceAwarder.accept(2L);
                center.getWorld().playSound(center, org.bukkit.Sound.BLOCK_BELL_USE, 0.8f, 1.1f);
                nextActionTick = serverTick + 200L;
            }
            phase = FarmerPhase.ALERTING;
            return;
        }
        clearHand();
        if (phase == FarmerPhase.PATROLLING) {
            double margin = config.navigationDistanceMargin();
            if (station != null && npc.getEntity().getLocation().distanceSquared(station) <= margin * margin) {
                if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
                station = null;
                phase = FarmerPhase.RESTING;
                nextActionTick = serverTick + 160L;
            } else if (!npc.getNavigator().isNavigating()
                    || serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
                if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
                station = null;
                phase = FarmerPhase.RESTING;
                nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            }
            return;
        }
        if (serverTick < nextActionTick) return;
        Location patrol = randomPatrol(center);
        if (patrol != null) {
            navigate(patrol, serverTick, config);
            phase = FarmerPhase.PATROLLING;
            return;
        }
        nextActionTick = serverTick + 160L;
    }

    private void navigate(Location target, long serverTick, LivingNpcConfig config) {
        Navigator navigator = npc.getNavigator();
        LivingNavigation.allowDoors(navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(config.navigationDistanceMargin())
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        navigator.setTarget(target);
        station = target;
        navigationStartedTick = serverTick;
        phase = FarmerPhase.GOING_TO_WORK_STATION;
    }

    private Location safeStandingNear(Location center, Location current) {
        Location best = null;
        double distance = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            Block feet = center.getWorld().getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z);
            if (!safe(feet)) continue;
            Location candidate = feet.getLocation().add(0.5, 0, 0.5);
            if (!npc.getNavigator().canNavigateTo(candidate)) continue;
            double candidateDistance = current.distanceSquared(candidate);
            if (candidateDistance < distance) {
                best = candidate;
                distance = candidateDistance;
            }
        }
        return best;
    }

    private Location randomPatrol(Location center) {
        for (int attempt = 0; attempt < 8; attempt++) {
            Block feet = center.getWorld().getBlockAt(
                    center.getBlockX() + ThreadLocalRandom.current().nextInt(-6, 7), center.getBlockY(),
                    center.getBlockZ() + ThreadLocalRandom.current().nextInt(-6, 7));
            if (safe(feet)) return feet.getLocation().add(0.5, 0, 0.5);
        }
        return safeStandingNear(center, npc.getEntity().getLocation());
    }

    private boolean safe(Block feet) {
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                && feet.getRelative(0, -1, 0).getType().isSolid();
    }

    private void face(Location target) {
        Location origin = npc.getEntity().getLocation();
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        npc.getEntity().setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0.0f);
    }

    private void setHand(ItemStack item) {
        npc.getOrAddTrait(Equipment.class).set(Equipment.EquipmentSlot.HAND, item);
        if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity living) {
            living.getEquipment().setItemInMainHand(item);
        }
    }

    private void clearHand() {
        setHand(null);
    }

    private void clearMiningDamage() {
        if (miningBlock != null && miningBlock.getWorld().isChunkLoaded(miningBlock.getChunk())) {
            sendMiningDamage(0.0f);
        }
    }

    private void sendMiningDamage(float progress) {
        if (miningBlock == null) return;
        miningBlock.getWorld().getNearbyPlayers(miningBlock.getLocation(), 48.0)
                .forEach(player -> player.sendBlockDamage(
                        miningBlock.getLocation(), progress, npc.getEntity()));
    }

    private record Recipe(Map<String, Integer> inputs, String output, int amount, String action) {
    }

    private record MiningCandidate(Block block, double distanceSquared) {
    }
}
