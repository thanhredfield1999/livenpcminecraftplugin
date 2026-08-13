package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

final class FisherManager {
    private final FarmerManager residents;
    private final NpcEconomy economy;
    private final VillageStore villages;
    private final Map<UUID, FisherRuntime> runtimes = new HashMap<>();
    private final Map<UUID, Long> nextRuntimeErrorLogTick = new HashMap<>();

    FisherManager(FarmerManager residents, NpcEconomy economy, VillageStore villages) {
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
            FisherRuntime runtime = runtimes.computeIfAbsent(definition.npcUuid(), ignored ->
                    new FisherRuntime(npc, definition, economy, villages,
                            amount -> residents.awardExperience(definition.npcUuid(), ResidentRole.FISHER, amount)));
            runtime.updateDefinition(definition);
            if (residents.roleChangedThisTick(definition.npcUuid())) continue;
            if (residents.sleeping(definition.npcUuid())) {
                runtime.releaseWorkState();
                continue;
            }
            if (definition.activeRole() == ResidentRole.FISHER) {
                try {
                    runtime.tick(serverTick, config);
                } catch (RuntimeException exception) {
                    runtime.suspend();
                    if (serverTick >= nextRuntimeErrorLogTick.getOrDefault(definition.npcUuid(), 0L)) {
                        org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                                "LivingNPC fisher tick failed for " + definition.npcUuid()
                                        + "; other fishers will continue", exception);
                        nextRuntimeErrorLogTick.put(definition.npcUuid(), serverTick + 1200L);
                    }
                }
            }
        }
    }

    FarmerPhase phase(UUID npcUuid) {
        FisherRuntime runtime = runtimes.get(npcUuid);
        return runtime == null ? null : runtime.phase();
    }

    void shutdown() {
        runtimes.values().forEach(FisherRuntime::suspend);
        runtimes.clear();
        nextRuntimeErrorLogTick.clear();
    }
}
