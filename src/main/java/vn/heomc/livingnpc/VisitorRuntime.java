package vn.heomc.livingnpc;

import java.util.UUID;
import java.util.List;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

final class VisitorRuntime {
    private final NPC npc;
    private final List<NPC> presentation;
    private final String villageId;
    private final Location gate;
    private final Location market;
    private final UUID merchantUuid;
    private final VisitorDemandSnapshot demand;
    private final long expiresAtTick;
    private VisitorPhase phase = VisitorPhase.GOING_TO_MARKET;
    private long navigationStartedTick;
    private long nextActionTick;
    private long nextFormationTick;
    private boolean purchased;

    VisitorRuntime(
            NPC npc, List<NPC> presentation, String villageId, Location gate, Location market, UUID merchantUuid,
            VisitorDemandSnapshot demand, long serverTick, LivingNpcConfig config) {
        this.npc = npc;
        this.presentation = List.copyOf(presentation);
        this.villageId = villageId;
        this.gate = gate.clone();
        this.market = market.clone();
        this.merchantUuid = merchantUuid;
        this.demand = demand;
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

    String visitId() {
        return demand.visitId();
    }

    boolean tick(long serverTick, LivingNpcConfig config, NpcEconomy economy, MerchantManager merchants) {
        if (!npc.isSpawned() || serverTick >= expiresAtTick) return false;
        tickFormation(serverTick, config);
        if (phase == VisitorPhase.SHOPPING) {
            if (!merchants.open(merchantUuid)) {
                phase = VisitorPhase.GOING_TO_GATE;
                navigate(gate, serverTick, config);
                return true;
            }
            if (!purchased) {
                economy.visitorPurchase(villageId, demand);
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
        for (NPC member : presentation) {
            if (member.getNavigator().isNavigating()) member.getNavigator().cancelNavigation();
            member.destroy();
        }
    }

    private void tickFormation(long serverTick, LivingNpcConfig config) {
        if (serverTick < nextFormationTick || !npc.isSpawned()) return;
        nextFormationTick = serverTick + 40L;
        Location leader = npc.getEntity().getLocation();
        org.bukkit.util.Vector direction = leader.getDirection().setY(0.0);
        if (direction.lengthSquared() < 0.01) direction.setZ(1.0);
        direction.normalize().multiply(-config.seasonFive().formationSpacing());
        for (int index = 0; index < presentation.size(); index++) {
            NPC member = presentation.get(index);
            if (!member.isSpawned() || !member.getEntity().getWorld().equals(leader.getWorld())) continue;
            Location target = leader.clone().add(direction.clone().multiply(index + 1));
            if (member.getEntity().getLocation().distanceSquared(target) < 2.25) continue;
            Navigator navigator = member.getNavigator();
            LivingNavigation.allowDoors(navigator.getLocalParameters())
                    .speedModifier(config.navigationSpeedModifier())
                    .distanceMargin(1.25)
                    .pathDistanceMargin(1.25)
                    .destinationTeleportMargin(0.0)
                    .stuckAction((stuckNpc, stuckNavigator) -> false);
            navigator.setTarget(target);
        }
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
