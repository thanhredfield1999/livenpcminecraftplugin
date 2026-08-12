package vn.heomc.livingnpc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

final class VisitorManager {
    private static final String[] NAMES = {
            "Aldous", "Cedric", "Elowen", "Merek", "Rosamund", "Tobias", "Winifred", "Yvette"
    };
    private final VillageStore villages;
    private final NpcEconomy economy;
    private final MerchantManager merchants;
    private final Map<UUID, VisitorRuntime> active = new LinkedHashMap<>();
    private final Map<String, Long> nextSpawnByVillage = new LinkedHashMap<>();

    VisitorManager(VillageStore villages, NpcEconomy economy, MerchantManager merchants) {
        this.villages = villages;
        this.economy = economy;
        this.merchants = merchants;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        for (VisitorRuntime visitor : java.util.List.copyOf(active.values())) {
            if (!visitor.tick(serverTick, config, economy, merchants)) {
                visitor.destroy();
                merchants.release(visitor.merchantUuid());
                active.remove(visitor.uuid());
            }
        }
        VisitorSettings settings = config.visitors();
        if (!settings.enabled() || active.size() >= settings.maxActive()) return;
        for (VillageDefinition village : villages.villages()) {
            if (active.size() >= settings.maxActive()) break;
            long nextSpawn = nextSpawnByVillage.getOrDefault(village.id(), 0L);
            if (serverTick < nextSpawn) continue;
            scheduleNext(village.id(), serverTick, settings);
            trySpawn(village, serverTick, config);
        }
    }

    int activeCount(String villageId) {
        return (int) active.values().stream().filter(visitor -> visitor.villageId().equals(villageId)).count();
    }

    void shutdown() {
        for (VisitorRuntime visitor : active.values()) {
            visitor.destroy();
            merchants.release(visitor.merchantUuid());
        }
        active.clear();
    }

    private void trySpawn(VillageDefinition village, long serverTick, LivingNpcConfig config) {
        Location gate = village.visitorGate() == null ? null : village.visitorGate().resolve();
        MerchantStall stall = merchants.reserveOpenStall(village.id());
        Location market = stall == null ? null : stall.buyerPoint().resolve();
        if (gate == null || market == null || !gate.getWorld().equals(market.getWorld())
                || !chunksLoaded(gate, market)) {
            if (stall != null) merchants.release(stall.merchantUuid());
            return;
        }
        double range = config.visitors().activationRange();
        if (gate.getWorld().getNearbyPlayers(gate, range).isEmpty()
                && market.getWorld().getNearbyPlayers(market, range).isEmpty()) {
            merchants.release(stall.merchantUuid());
            return;
        }
        NPC npc = CitizensAPI.getTemporaryNPCRegistry().createNPC(
                EntityType.PLAYER, NAMES[ThreadLocalRandom.current().nextInt(NAMES.length)]);
        npc.setProtected(true);
        npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
        if (!npc.spawn(gate)) {
            npc.destroy();
            merchants.release(stall.merchantUuid());
            return;
        }
        long wallet = ThreadLocalRandom.current().nextLong(
                config.visitors().walletMinMinor(), config.visitors().walletMaxMinor() + 1L);
        VisitorRuntime visitor = new VisitorRuntime(
                npc, village.id(), gate, market, stall.merchantUuid(), wallet, serverTick, config);
        active.put(visitor.uuid(), visitor);
    }

    private void scheduleNext(String villageId, long serverTick, VisitorSettings settings) {
        long interval = ThreadLocalRandom.current().nextLong(
                settings.spawnIntervalMinTicks(), settings.spawnIntervalMaxTicks() + 1L);
        nextSpawnByVillage.put(villageId, serverTick + interval);
    }

    private boolean chunksLoaded(Location first, Location second) {
        return first.getWorld().isChunkLoaded(first.getBlockX() >> 4, first.getBlockZ() >> 4)
                && second.getWorld().isChunkLoaded(second.getBlockX() >> 4, second.getBlockZ() >> 4);
    }
}
