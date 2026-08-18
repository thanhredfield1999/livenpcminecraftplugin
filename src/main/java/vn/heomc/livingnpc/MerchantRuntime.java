package vn.heomc.livingnpc;

import java.util.UUID;
import java.util.Collection;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.ai.Navigator;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class MerchantRuntime {
    private final NPC npc;
    private FarmerDefinition definition;
    private FarmerPhase phase = FarmerPhase.INACTIVE;
    private long navigationStartedTick;
    private boolean open;

    MerchantRuntime(NPC npc, FarmerDefinition definition) {
        this.npc = npc;
        this.definition = definition;
    }

    UUID npcUuid() {
        return npc.getUniqueId();
    }

    FarmerPhase phase() {
        return phase;
    }

    boolean open() {
        return open;
    }

    void updateDefinition(FarmerDefinition updated) {
        if (definition.activeRole() != updated.activeRole()
                || !java.util.Objects.equals(definition.villageId(), updated.villageId())) suspend();
        definition = updated;
    }

    void tick(long serverTick, LivingNpcConfig config, VillageDefinition village) {
        MerchantStall stall = village == null ? null : village.merchantStall(npcUuid());
        Location seller = stall == null || !stall.complete() ? null : stall.sellerPoint().resolve();
        if (!npc.isSpawned() || definition.activeRole() != ResidentRole.MERCHANT
                || !definition.enabled(BehaviorFlag.MASTER) || seller == null) {
            suspend();
            return;
        }
        Location current = npc.getEntity().getLocation();
        if (!current.getWorld().equals(seller.getWorld())) {
            suspend();
            return;
        }
        Collection<Player> nearby = seller.getWorld().getNearbyPlayers(seller, config.activationRange());
        if (nearby.isEmpty() && current.getWorld().getNearbyPlayers(current, config.activationRange()).isEmpty()) {
            suspend();
            return;
        }
        ResidentSchedule schedule = definition.schedule(
                ResidentRole.MERCHANT, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        boolean workTime = !definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                || SchedulePolicy.isWorkTime(current.getWorld().getTime(), current.getWorld().hasStorm(), schedule);
        if (!workTime) {
            closeAndReturnHome(serverTick, config);
            return;
        }
        double margin = config.navigationDistanceMargin();
        if (current.distanceSquared(seller) <= margin * margin) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            npc.getEntity().setRotation(stall.sellerPoint().yaw(), 0.0f);
            open = true;
            phase = FarmerPhase.SERVING;
            return;
        }
        open = false;
        if (phase == FarmerPhase.GOING_TO_STALL && !npc.getNavigator().isNavigating()) {
            suspend();
        } else if (phase != FarmerPhase.GOING_TO_STALL) {
            navigate(seller, FarmerPhase.GOING_TO_STALL, serverTick, config);
        } else if (serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
            NavigationRecovery.Result recovery = NavigationRecovery.recover(
                    npc, seller, 2, serverTick, "GOING_TO_STALL", config.navigationRetryBackoffTicks());
            if (recovery == NavigationRecovery.Result.RECOVERED) {
                npc.getEntity().setRotation(stall.sellerPoint().yaw(), 0.0f);
                open = true;
                phase = FarmerPhase.SERVING;
            }
        }
    }

    void suspend() {
        open = false;
        if (npc.isSpawned() && npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        phase = FarmerPhase.INACTIVE;
    }

    void releaseForSleep() {
        open = false;
        if (npc.isSpawned() && npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        phase = FarmerPhase.INACTIVE;
    }

    private void closeAndReturnHome(long serverTick, LivingNpcConfig config) {
        open = false;
        Location home = definition.home().resolve();
        if (home == null || !npc.getEntity().getWorld().equals(home.getWorld())) {
            suspend();
            return;
        }
        if (npc.getEntity().getLocation().distanceSquared(home) <= 9.0) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            phase = FarmerPhase.INACTIVE;
            return;
        }
        if (phase == FarmerPhase.GOING_HOME && !npc.getNavigator().isNavigating()) {
            suspend();
        } else if (phase != FarmerPhase.GOING_HOME) {
            navigate(home, FarmerPhase.GOING_HOME, serverTick, config);
        } else if (serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
            NavigationRecovery.recover(
                    npc, home, 2, serverTick, "GOING_HOME", config.navigationRetryBackoffTicks());
        }
    }

    private void navigate(Location target, FarmerPhase nextPhase, long serverTick, LivingNpcConfig config) {
        Navigator navigator = npc.getNavigator();
        navigator.cancelNavigation();
        if (!MovementService.startSimpleNavigation(
                navigator, target, config.navigationSpeedModifier(), config.navigationDistanceMargin())) {
            return;
        }
        navigationStartedTick = serverTick;
        phase = nextPhase;
    }
}
