package vn.heomc.livingnpc;

import java.util.UUID;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

final class VisitorRuntime {
    private final NPC npc;
    private final String villageId;
    private final Location gate;
    private final Location market;
    private final UUID merchantUuid;
    private final long walletMinor;
    private final long expiresAtTick;
    private VisitorPhase phase = VisitorPhase.GOING_TO_MARKET;
    private long navigationStartedTick;
    private long nextActionTick;
    private boolean purchased;

    VisitorRuntime(
            NPC npc, String villageId, Location gate, Location market, UUID merchantUuid,
            long walletMinor, long serverTick, LivingNpcConfig config) {
        this.npc = npc;
        this.villageId = villageId;
        this.gate = gate.clone();
        this.market = market.clone();
        this.merchantUuid = merchantUuid;
        this.walletMinor = walletMinor;
        this.expiresAtTick = serverTick + config.visitors().lifetimeTicks();
        navigate(market, serverTick, config);
    }

    UUID uuid() {
        return npc.getUniqueId();
    }

    String villageId() {
        return villageId;
    }

    ResidentRole role() {
        return ResidentRole.VISITOR;
    }

    UUID merchantUuid() {
        return merchantUuid;
    }

    boolean tick(long serverTick, LivingNpcConfig config, NpcEconomy economy, MerchantManager merchants) {
        if (!npc.isSpawned() || serverTick >= expiresAtTick) return false;
        if (phase == VisitorPhase.SHOPPING) {
            if (!merchants.open(merchantUuid)) {
                phase = VisitorPhase.GOING_TO_GATE;
                navigate(gate, serverTick, config);
                return true;
            }
            if (!purchased) {
                economy.visitorPurchase(
                        villageId, "visitor:" + npc.getUniqueId(), walletMinor,
                        config.visitors().maxPurchaseItems());
                purchased = true;
            }
            if (serverTick >= nextActionTick) {
                phase = VisitorPhase.GOING_TO_GATE;
                navigate(gate, serverTick, config);
            }
            return true;
        }
        Location target = phase == VisitorPhase.GOING_TO_MARKET ? market : gate;
        if (!npc.getEntity().getWorld().equals(target.getWorld())) return false;
        double margin = config.navigationDistanceMargin();
        if (npc.getEntity().getLocation().distanceSquared(target) <= margin * margin) {
            if (phase == VisitorPhase.GOING_TO_GATE) return false;
            npc.getNavigator().cancelNavigation();
            phase = VisitorPhase.SHOPPING;
            nextActionTick = serverTick + config.visitors().shoppingDurationTicks();
            return true;
        }
        if (serverTick - navigationStartedTick >= config.navigationTimeoutTicks()
                || !npc.getNavigator().isNavigating()) {
            return false;
        }
        return true;
    }

    void destroy() {
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        npc.destroy();
    }

    private void navigate(Location target, long serverTick, LivingNpcConfig config) {
        Navigator navigator = npc.getNavigator();
        navigator.cancelNavigation();
        LivingNavigation.allowDoors(navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(config.navigationDistanceMargin())
                .pathDistanceMargin(config.navigationDistanceMargin())
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        navigator.setTarget(target);
        navigationStartedTick = serverTick;
    }
}
