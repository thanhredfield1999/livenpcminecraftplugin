package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

final class CombatManager implements Listener {
    private static final int MAX_KILLS_PER_RUN = 32;
    private static final long REWARD_MINOR = 100L;
    private final CombatArenaStore store;
    private final Map<String, CombatArena> arenas;
    private final FarmerManager farmers;
    private final NpcEconomy economy;
    private final Map<UUID, Long> nextAttackTick = new LinkedHashMap<>();
    private final Set<String> retreating = new java.util.HashSet<>();
    private final Map<String, Long> retreatDeadlines = new LinkedHashMap<>();
    private final Map<UUID, ItemStack> previousHands = new LinkedHashMap<>();

    CombatManager(CombatArenaStore store, FarmerManager farmers, NpcEconomy economy) {
        this.store = store;
        this.farmers = farmers;
        this.economy = economy;
        arenas = store.load();
    }

    boolean create(String id, String villageId, int archerId, int swordsmanId, Location retreat) {
        String key = id.toLowerCase(java.util.Locale.ROOT);
        NPC archer = CitizensAPI.getNPCRegistry().getById(archerId);
        NPC swordsman = CitizensAPI.getNPCRegistry().getById(swordsmanId);
        FarmerDefinition archerDefinition = farmers.get(archerId);
        FarmerDefinition swordsmanDefinition = farmers.get(swordsmanId);
        boolean assigned = arenas.values().stream().anyMatch(arena ->
                arena.archerUuid().equals(archer == null ? null : archer.getUniqueId())
                        || arena.swordsmanUuid().equals(archer == null ? null : archer.getUniqueId())
                        || arena.archerUuid().equals(swordsman == null ? null : swordsman.getUniqueId())
                        || arena.swordsmanUuid().equals(swordsman == null ? null : swordsman.getUniqueId()));
        if (arenas.containsKey(key) || assigned || archer == null || swordsman == null || archer.equals(swordsman)
                || archerDefinition == null || swordsmanDefinition == null
                || !(archer.getEntity() instanceof LivingEntity) || !(swordsman.getEntity() instanceof LivingEntity)
                || !villageId.equals(archerDefinition.villageId()) || !villageId.equals(swordsmanDefinition.villageId())) {
            return false;
        }
        CombatArena arena = new CombatArena(key, villageId, archer.getUniqueId(), swordsman.getUniqueId(),
                null, null, StoredLocation.from(retreat), false, 0);
        arenas.put(key, arena);
        if (store.save(arenas)) return true;
        arenas.remove(key);
        return false;
    }

    boolean setCorner(String id, int corner, Location location) {
        CombatArena current = arena(id);
        if (current == null || corner < 1 || corner > 2 || current.active()) return false;
        CombatArena updated = current.withCorner(corner, StoredLocation.from(location));
        arenas.put(updated.id(), updated);
        if (store.save(arenas)) return true;
        arenas.put(current.id(), current);
        return false;
    }

    boolean setRetreat(String id, Location location) {
        CombatArena current = arena(id);
        if (current == null || current.active()) return false;
        CombatArena updated = current.withRetreatPoint(StoredLocation.from(location));
        arenas.put(updated.id(), updated);
        if (store.save(arenas)) return true;
        arenas.put(current.id(), current);
        return false;
    }

    boolean start(String id) {
        CombatArena current = arena(id);
        if (current == null || !current.configured() || current.active()) return false;
        NPC archer = npc(current.archerUuid());
        NPC swordsman = npc(current.swordsmanUuid());
        boolean activeElsewhere = arenas.values().stream().anyMatch(arena -> arena.active() && !arena.id().equals(current.id())
                && (arena.archerUuid().equals(current.archerUuid()) || arena.swordsmanUuid().equals(current.archerUuid())
                        || arena.archerUuid().equals(current.swordsmanUuid()) || arena.swordsmanUuid().equals(current.swordsmanUuid())));
        if (activeElsewhere || !livingInWorld(archer, current.firstCorner().world())
                || !livingInWorld(swordsman, current.firstCorner().world())) {
            return false;
        }
        arenas.put(current.id(), current.withActive(true));
        retreating.remove(current.id());
        rememberHand(archer);
        rememberHand(swordsman);
        farmers.setExternallyBusy(activeCombatants());
        equip(archer, Material.BOW);
        equip(swordsman, Material.IRON_SWORD);
        return true;
    }

    boolean stop(String id) {
        CombatArena current = arena(id);
        if (current == null || !current.active()) return false;
        beginRetreat(current);
        return true;
    }

    CombatArena arena(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    void tick(long serverTick) {
        Set<UUID> busy = new java.util.HashSet<>();
        for (CombatArena arena : arenas.values()) {
            if (arena.active()) {
                busy.add(arena.archerUuid());
                busy.add(arena.swordsmanUuid());
            }
        }
        farmers.setExternallyBusy(busy);
        for (CombatArena arena : java.util.List.copyOf(arenas.values())) {
            if (!arena.active()) continue;
            if (isBedtimeFor(arena)) {
                finish(arena);
                continue;
            }
            NPC archer = npc(arena.archerUuid());
            NPC swordsman = npc(arena.swordsmanUuid());
            if (retreating.contains(arena.id())) {
                if (!livingInWorld(archer, arena.retreatPoint().world())
                        || !livingInWorld(swordsman, arena.retreatPoint().world())
                        || System.currentTimeMillis() >= retreatDeadlines.getOrDefault(arena.id(), 0L)) {
                    finish(arena);
                    continue;
                }
                handleRetreat(arena, archer, swordsman);
                continue;
            }
            if (!livingInWorld(archer, arena.firstCorner().world())
                    || !livingInWorld(swordsman, arena.firstCorner().world())) {
                finish(arena);
                continue;
            }
            if (healthRatio(archer) <= 0.4 || healthRatio(swordsman) <= 0.4
                    || arena.killsThisRun() >= MAX_KILLS_PER_RUN) {
                beginRetreat(arena);
                continue;
            }
            Zombie target = nearestZombie(arena, swordsman.getEntity().getLocation());
            if (target == null) {
                cancelNavigation(archer);
                cancelNavigation(swordsman);
                continue;
            }
            handleArcher(arena, archer, target, serverTick);
            handleSwordsman(arena, swordsman, target, serverTick);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());
        if (npc == null) return;
        CombatArena arena = activeArenaFor(npc.getUniqueId());
        if (arena == null) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (living.getHealth() - event.getFinalDamage() <= living.getMaxHealth() * 0.4) {
            event.setCancelled(true);
            beginRetreat(arena);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onZombieDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        org.bukkit.entity.Entity attacker = event.getDamageSource().getCausingEntity();
        NPC killer = attacker == null ? null : CitizensAPI.getNPCRegistry().getNPC(attacker);
        CombatArena arena = killer == null ? null : activeArenaFor(killer.getUniqueId());
        if (arena == null || !arena.active() || !arena.contains(zombie.getLocation())
                || arena.killsThisRun() >= MAX_KILLS_PER_RUN) return;
        economy.creditVillage(arena.villageId(), REWARD_MINOR);
        arenas.put(arena.id(), arena.withKills(arena.killsThisRun() + 1));
    }

    private void handleArcher(CombatArena arena, NPC archer, Zombie target, long serverTick) {
        Location from = archer.getEntity().getLocation();
        double distance = from.distance(target.getLocation());
        if (distance > 14.0) {
            navigate(archer, target.getLocation(), 1.2);
            return;
        }
        cancelNavigation(archer);
        archer.faceLocation(target.getEyeLocation());
        if (serverTick < nextAttackTick.getOrDefault(archer.getUniqueId(), 0L)) return;
        Location eye = ((LivingEntity) archer.getEntity()).getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        Arrow arrow = eye.getWorld().spawnArrow(eye, direction, 1.8f, 1.0f);
        arrow.setShooter((LivingEntity) archer.getEntity());
        arrow.setDamage(3.0);
        nextAttackTick.put(archer.getUniqueId(), serverTick + 30L);
    }

    private void handleSwordsman(CombatArena arena, NPC swordsman, Zombie target, long serverTick) {
        Location from = swordsman.getEntity().getLocation();
        if (from.distanceSquared(target.getLocation()) > 7.0) {
            navigate(swordsman, target.getLocation(), 1.5);
            return;
        }
        cancelNavigation(swordsman);
        swordsman.faceLocation(target.getEyeLocation());
        if (serverTick < nextAttackTick.getOrDefault(swordsman.getUniqueId(), 0L)) return;
        ((LivingEntity) swordsman.getEntity()).swingMainHand();
        target.damage(4.0, swordsman.getEntity());
        nextAttackTick.put(swordsman.getUniqueId(), serverTick + 20L);
    }

    private void beginRetreat(CombatArena arena) {
        retreating.add(arena.id());
        retreatDeadlines.put(arena.id(), System.currentTimeMillis() + 20_000L);
        Location retreat = arena.retreatPoint().resolve();
        if (retreat == null) {
            finish(arena);
            return;
        }
        NPC archer = npc(arena.archerUuid());
        NPC swordsman = npc(arena.swordsmanUuid());
        if (!livingInWorld(archer, retreat.getWorld().getName()) || !livingInWorld(swordsman, retreat.getWorld().getName())) {
            finish(arena);
            return;
        }
        navigate(archer, retreat, 1.5);
        navigate(swordsman, retreat, 1.5);
    }

    private void handleRetreat(CombatArena arena, NPC archer, NPC swordsman) {
        Location retreat = arena.retreatPoint().resolve();
        if (retreat == null) {
            finish(arena);
            return;
        }
        if (archer.getEntity().getLocation().distanceSquared(retreat) <= 9.0
                && swordsman.getEntity().getLocation().distanceSquared(retreat) <= 9.0) {
            heal(archer);
            heal(swordsman);
            finish(arena);
        }
    }

    private void finish(CombatArena arena) {
        NPC archer = npc(arena.archerUuid());
        NPC swordsman = npc(arena.swordsmanUuid());
        cancelNavigation(archer);
        cancelNavigation(swordsman);
        clearHand(archer);
        clearHand(swordsman);
        retreating.remove(arena.id());
        retreatDeadlines.remove(arena.id());
        arenas.put(arena.id(), arena.withActive(false));
        economy.flush();
        farmers.setExternallyBusy(activeCombatants());
    }

    private Zombie nearestZombie(CombatArena arena, Location from) {
        Location first = arena.firstCorner().resolve();
        Location second = arena.secondCorner().resolve();
        if (first == null || second == null) return null;
        double x = Math.abs(first.getX() - second.getX()) / 2.0 + 1.0;
        double y = Math.abs(first.getY() - second.getY()) / 2.0 + 1.0;
        double z = Math.abs(first.getZ() - second.getZ()) / 2.0 + 1.0;
        Location center = first.clone().add(second).multiply(0.5);
        return center.getWorld().getNearbyEntitiesByType(Zombie.class, center, x, y, z).stream()
                .filter(zombie -> arena.contains(zombie.getLocation()))
                .min(Comparator.comparingDouble(zombie -> zombie.getLocation().distanceSquared(from)))
                .orElse(null);
    }

    private CombatArena activeArenaFor(UUID npcUuid) {
        return arenas.values().stream()
                .filter(CombatArena::active)
                .filter(arena -> arena.archerUuid().equals(npcUuid) || arena.swordsmanUuid().equals(npcUuid))
                .findFirst().orElse(null);
    }

    private NPC npc(UUID uuid) {
        return CitizensAPI.getNPCRegistry().getByUniqueId(uuid);
    }

    private boolean livingInWorld(NPC npc, String world) {
        return npc != null && npc.isSpawned() && npc.getEntity() instanceof LivingEntity
                && npc.getEntity().getWorld().getName().equals(world);
    }

    private double healthRatio(NPC npc) {
        LivingEntity living = (LivingEntity) npc.getEntity();
        return living.getHealth() / living.getMaxHealth();
    }

    private void heal(NPC npc) {
        LivingEntity living = (LivingEntity) npc.getEntity();
        living.setHealth(living.getMaxHealth());
    }

    private void equip(NPC npc, Material material) {
        npc.getOrAddTrait(Equipment.class).set(EquipmentSlot.HAND, new ItemStack(material));
    }

    private void rememberHand(NPC npc) {
        ItemStack hand = npc.getOrAddTrait(Equipment.class).get(EquipmentSlot.HAND);
        previousHands.put(npc.getUniqueId(), hand == null ? null : hand.clone());
    }

    private void clearHand(NPC npc) {
        if (npc != null) {
            npc.getOrAddTrait(Equipment.class).set(EquipmentSlot.HAND, previousHands.remove(npc.getUniqueId()));
        }
    }

    private void navigate(NPC npc, Location target, double margin) {
        if (!npc.getEntity().getWorld().equals(target.getWorld())) return;
        Navigator navigator = npc.getNavigator();
        if (navigator.isNavigating() && navigator.getTargetAsLocation() != null
                && navigator.getTargetAsLocation().distanceSquared(target) < 4.0) return;
        navigator.setTarget(target);
        navigator.getLocalParameters().speedModifier(1.0f).distanceMargin(margin).pathDistanceMargin(margin);
    }

    private void cancelNavigation(NPC npc) {
        if (npc != null && npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
    }

    void shutdown() {
        for (CombatArena arena : java.util.List.copyOf(arenas.values())) {
            if (arena.active()) finish(arena);
        }
        nextAttackTick.clear();
        retreating.clear();
        retreatDeadlines.clear();
        farmers.setExternallyBusy(Set.of());
    }

    private Set<UUID> activeCombatants() {
        Set<UUID> busy = new java.util.HashSet<>();
        for (CombatArena arena : arenas.values()) {
            if (arena.active()) {
                busy.add(arena.archerUuid());
                busy.add(arena.swordsmanUuid());
            }
        }
        return busy;
    }

    static boolean isBedtimeFor(CombatArena arena) {
        StoredLocation first = arena.firstCorner();
        if (first == null) return false;
        Location location = first.resolve();
        return location != null && FarmerRuntime.isBedtime(location.getWorld().getTime());
    }
}
