package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SitTrait;
import org.bukkit.Location;

final class SeatManager {
    private final VillageStore villages;
    private final Map<String, UUID> ownersBySeat = new HashMap<>();
    private final Map<UUID, SeatDefinition> seatsByNpc = new HashMap<>();

    SeatManager(VillageStore villages) {
        this.villages = villages;
    }

    SeatDefinition reserveClosest(
            UUID npcUuid, String villageId, SeatType type, Location from,
            Predicate<Location> canNavigateTo) {
        SeatDefinition current = seatsByNpc.get(npcUuid);
        if (current != null) return current.type() == type ? current : null;
        VillageDefinition village = villages.get(villageId);
        if (village == null || from == null) return null;
        return village.seats().stream()
                .filter(seat -> seat.type() == type)
                .filter(SeatValidator::stillValid)
                .filter(seat -> !ownersBySeat.containsKey(seat.id()))
                .filter(seat -> seat.location().world().equals(from.getWorld().getName()))
                .sorted(Comparator.comparingDouble(seat -> distanceSquared(seat, from)))
                .filter(seat -> {
                    Location approach = SeatValidator.approachLocation(seat);
                    return approach != null && canNavigateTo.test(approach);
                })
                .findFirst()
                .map(seat -> {
                    ownersBySeat.put(seat.id(), npcUuid);
                    seatsByNpc.put(npcUuid, seat);
                    return seat;
                })
                .orElse(null);
    }

    SeatDefinition seat(UUID npcUuid) {
        return seatsByNpc.get(npcUuid);
    }

    boolean sit(NPC npc) {
        SeatDefinition seat = seatsByNpc.get(npc.getUniqueId());
        Location location = seat == null ? null : seat.location().resolve();
        if (location == null || !SeatValidator.stillValid(seat)) {
            release(npc);
            return false;
        }
        location.setYaw(seat.location().yaw());
        location.setPitch(0.0f);
        npc.getOrAddTrait(SitTrait.class).setSitting(location);
        lockRotation(npc);
        return true;
    }

    void lockRotation(NPC npc) {
        SeatDefinition seat = seatsByNpc.get(npc.getUniqueId());
        if (seat != null && npc.isSpawned()) {
            npc.getEntity().setRotation(seat.location().yaw(), 0.0f);
        }
    }

    void release(NPC npc) {
        SeatDefinition seat = seatsByNpc.remove(npc.getUniqueId());
        if (seat != null) ownersBySeat.remove(seat.id(), npc.getUniqueId());
        if (npc.hasTrait(SitTrait.class)) npc.getTraitNullable(SitTrait.class).setSitting(null);
    }

    void shutdown(Iterable<NPC> npcs) {
        for (NPC npc : npcs) release(npc);
        ownersBySeat.clear();
        seatsByNpc.clear();
    }

    private double distanceSquared(SeatDefinition seat, Location from) {
        Location location = seat.location().resolve();
        return location == null ? Double.MAX_VALUE : location.distanceSquared(from);
    }
}
