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
        net.citizensnpcs.api.npc.NPCRegistry registry = registryOrNull();
        if (registry == null) return;
        java.util.Set<UUID> current = new java.util.HashSet<>();
        Map<UUID, NPC> availableNpcs = new HashMap<>();
        for (FarmerDefinition definition : definitions) {
            UUID npcUuid = definition.npcUuid();
            try {
                NPC npc = registry.getByUniqueId(npcUuid);
                if (npc != null) {
                    current.add(npcUuid);
                    availableNpcs.put(npcUuid, npc);
                }
            } catch (RuntimeException exception) {
                current.add(npcUuid);
                logRuntimeFailure(serverTick, npcUuid, "Citizens lookup failed", exception);
            }
        }
        for (Map.Entry<UUID, FisherRuntime> entry : java.util.List.copyOf(runtimes.entrySet())) {
            if (current.contains(entry.getKey())) continue;
            try {
                entry.getValue().suspend();
            } catch (RuntimeException exception) {
                logRuntimeFailure(serverTick, entry.getKey(), "stale runtime cleanup failed", exception);
            } finally {
                runtimes.remove(entry.getKey(), entry.getValue());
                nextRuntimeErrorLogTick.remove(entry.getKey());
            }
        }
        for (FarmerDefinition definition : definitions) {
            UUID npcUuid = definition.npcUuid();
            NPC npc = availableNpcs.get(npcUuid);
            if (npc == null) continue;
            FisherRuntime runtime;
            try {
                runtime = runtimes.computeIfAbsent(npcUuid, ignored ->
                        new FisherRuntime(npc, definition, economy, villages,
                                residents.navigationLeases(),
                                amount -> residents.awardExperience(npcUuid, ResidentRole.FISHER, amount)));
            } catch (RuntimeException exception) {
                logRuntimeFailure(serverTick, npcUuid, "runtime creation failed", exception);
                continue;
            }
            try {
                runtime.updateDefinition(definition);
            } catch (RuntimeException exception) {
                logRuntimeFailure(serverTick, npcUuid, "definition cleanup failed", exception);
                continue;
            }
            try {
                if (residents.roleChangedThisTick(npcUuid)) continue;
            } catch (RuntimeException exception) {
                logRuntimeFailure(serverTick, npcUuid, "role state lookup failed", exception);
                continue;
            }
            boolean sleeping;
            try {
                sleeping = residents.sleeping(npcUuid);
            } catch (RuntimeException exception) {
                logRuntimeFailure(serverTick, npcUuid, "sleep state lookup failed", exception);
                continue;
            }
            if (sleeping) {
                try {
                    runtime.releaseForSleep();
                } catch (RuntimeException exception) {
                    logRuntimeFailure(serverTick, npcUuid, "sleep cleanup failed", exception);
                }
                continue;
            }
            ResidentRole activeRole;
            try {
                activeRole = definition.activeRole();
            } catch (RuntimeException exception) {
                logRuntimeFailure(serverTick, npcUuid, "active role lookup failed", exception);
                continue;
            }
            if (activeRole == ResidentRole.FISHER) {
                try {
                    runtime.tick(serverTick, config);
                } catch (RuntimeException exception) {
                    try {
                        runtime.suspend();
                    } catch (RuntimeException cleanupException) {
                        if (exception != cleanupException) exception.addSuppressed(cleanupException);
                    }
                    logRuntimeFailure(serverTick, npcUuid, "tick failed", exception);
                }
            }
        }
    }

    FarmerPhase phase(UUID npcUuid) {
        FisherRuntime runtime = runtimes.get(npcUuid);
        return runtime == null ? null : runtime.phase();
    }

    private void logRuntimeFailure(
            long serverTick, UUID npcUuid, String operation, RuntimeException exception) {
        if (serverTick < nextRuntimeErrorLogTick.getOrDefault(npcUuid, 0L)) return;
        org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                "LivingNPC fisher " + operation + " for " + npcUuid
                        + "; other fishers will continue", exception);
        nextRuntimeErrorLogTick.put(npcUuid, serverTick + 1200L);
    }

    void shutdown() {
        RuntimeException failure = null;
        for (FisherRuntime runtime : java.util.List.copyOf(runtimes.values())) {
            try {
                runtime.suspend();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
        }
        runtimes.clear();
        nextRuntimeErrorLogTick.clear();
        if (failure != null) throw failure;
    }

    /**
     * Citizens chưa luôn publish implementation khi plugin đang enable. API hiện tại ném
     * {@link IllegalStateException} thay vì trả {@code null}; runtime phải chờ tick sau.
     */
    private static net.citizensnpcs.api.npc.NPCRegistry registryOrNull() {
        try {
            return CitizensAPI.getNPCRegistry();
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }
}
