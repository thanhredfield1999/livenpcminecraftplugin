package vn.heomc.livingnpc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

final class NeedsManager {
    private final FarmerManager residents;
    private final NeedsStore store;
    private final Map<UUID, ResidentNeeds> needs;
    private long lastTick = -1L;
    private long nextSaveTick;
    private boolean dirty;

    NeedsManager(FarmerManager residents, NeedsStore store) {
        this.residents = residents;
        this.store = store;
        needs = new LinkedHashMap<>(store.load());
    }

    void tick(long serverTick, NeedsSettings settings) {
        if (!settings.enabled()) {
            lastTick = serverTick;
            return;
        }
        long delta = lastTick < 0L ? 0L : Math.max(0L, serverTick - lastTick);
        lastTick = serverTick;
        for (FarmerDefinition definition : residents.definitions()) {
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            if (npc == null || !npc.isSpawned()) continue;
            Location location = npc.getEntity().getLocation();
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            ResidentNeeds value = needs.computeIfAbsent(definition.npcUuid(), uuid -> initial(uuid, location));
            dirty |= value.advance(delta, location.getWorld().getName(), settings);
        }
        if (dirty && serverTick >= nextSaveTick) {
            dirty = !store.save(needs);
            nextSaveTick = serverTick + settings.saveIntervalTicks();
        }
    }

    ResidentNeeds get(UUID npcUuid) {
        return needs.get(npcUuid);
    }

    void shutdown() {
        if (dirty) dirty = !store.save(needs);
    }

    private ResidentNeeds initial(UUID npcUuid, Location location) {
        int hunger = 55 + Math.floorMod(npcUuid.hashCode(), 21);
        int thirst = 45 + Math.floorMod(Long.hashCode(npcUuid.getMostSignificantBits()), 26);
        dirty = true;
        return new ResidentNeeds(npcUuid, location.getWorld().getName(), hunger, thirst);
    }
}
