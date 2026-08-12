package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

final class RancherManager implements Listener {
    private final FarmerManager residents;
    private final NpcEconomy economy;
    private final VillageStore villages;
    private final Map<UUID, RancherRuntime> runtimes = new HashMap<>();
    private final Map<UUID, CullOwner> pendingCull = new HashMap<>();
    private final RanchWorkCoordinator workCoordinator = new RanchWorkCoordinator();

    RancherManager(FarmerManager residents, NpcEconomy economy, VillageStore villages) {
        this.residents = residents;
        this.economy = economy;
        this.villages = villages;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        java.util.Collection<FarmerDefinition> definitions = residents.definitions();
        java.util.Set<UUID> current = definitions.stream().map(FarmerDefinition::npcUuid)
                .filter(uuid -> CitizensAPI.getNPCRegistry().getByUniqueId(uuid) != null)
                .collect(java.util.stream.Collectors.toSet());
        runtimes.entrySet().removeIf(entry -> {
            if (current.contains(entry.getKey())) return false;
            entry.getValue().suspend();
            return true;
        });
        for (FarmerDefinition definition : definitions) {
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            if (npc == null) continue;
            RancherRuntime runtime = runtimes.computeIfAbsent(
                    definition.npcUuid(), ignored -> new RancherRuntime(
                            npc, definition, economy, villages, workCoordinator,
                            animal -> cull(animal, definition.npcUuid(), definition.villageId()),
                            amount -> residents.awardExperience(
                                    definition.npcUuid(), ResidentRole.RANCHER, amount)));
            runtime.updateDefinition(definition);
            if (residents.roleChangedThisTick(definition.npcUuid())) continue;
            if (residents.sleeping(definition.npcUuid())) {
                runtime.releaseWorkState();
                continue;
            }
            if (definition.activeRole() == ResidentRole.RANCHER) runtime.tick(serverTick, config);
        }
    }

    void shutdown() {
        runtimes.values().forEach(RancherRuntime::suspend);
        runtimes.clear();
        pendingCull.clear();
        workCoordinator.clear();
    }

    String status(UUID npcUuid, LivingNpcConfig config) {
        RancherRuntime runtime = runtimes.get(npcUuid);
        return runtime == null ? "Runtime chăn nuôi chưa được tạo" : runtime.status(config);
    }

    FarmerPhase phase(UUID npcUuid) {
        RancherRuntime runtime = runtimes.get(npcUuid);
        return runtime == null ? null : runtime.phase();
    }

    boolean taskActive(UUID npcUuid) {
        RancherRuntime runtime = runtimes.get(npcUuid);
        return runtime != null && runtime.taskActive();
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        CullOwner owner = pendingCull.remove(event.getEntity().getUniqueId());
        if (owner == null) return;
        Map<String, Integer> loot = new HashMap<>();
        event.getDrops().forEach(drop -> loot.merge(
                drop.getType().getKey().getKey(), drop.getAmount(), Integer::sum));
        if (economy.addCarriedLoot(owner.npcUuid(), loot)) event.getDrops().clear();
    }

    private void cull(Animals animal, UUID npcUuid, String villageId) {
        pendingCull.put(animal.getUniqueId(), new CullOwner(npcUuid, villageId));
        animal.damage(Math.max(1000.0, animal.getHealth()));
        if (!animal.isDead()) pendingCull.remove(animal.getUniqueId());
    }

    private record CullOwner(UUID npcUuid, String villageId) {
    }
}
