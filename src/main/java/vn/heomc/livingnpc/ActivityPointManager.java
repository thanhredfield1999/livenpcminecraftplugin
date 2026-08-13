package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;

final class ActivityPointManager {
    private final VillageStore villages;
    private final Map<String, Set<UUID>> ownersByResource = new HashMap<>();
    private final Map<UUID, Reservation> reservationsByNpc = new HashMap<>();

    private record Reservation(String resourceId, ActivityPoint point) {
    }

    ActivityPointManager(VillageStore villages) {
        this.villages = villages;
    }

    ActivityPoint reserveClosest(
            UUID npcUuid, String villageId, ActivityPointType type, ResidentRole role,
            long worldTick, Location from, Predicate<Location> canNavigateTo) {
        Reservation current = reservationsByNpc.get(npcUuid);
        if (current != null) return current.point().type() == type ? current.point() : null;
        VillageDefinition village = villages.get(villageId);
        if (village == null || from == null) return null;
        return village.activityPoints().stream()
                .filter(point -> point.type() == type)
                .filter(point -> point.availableAt(worldTick, role, npcUuid))
                .filter(point -> point.standing().world().equals(from.getWorld().getName()))
                .filter(point -> ownersByResource.getOrDefault(resourceId(villageId, point), Set.of()).size()
                        < point.capacity())
                .sorted(Comparator.comparingDouble(point -> distanceSquared(point, from)))
                .limit(4)
                .filter(point -> {
                    Location standing = point.standing().resolve();
                    return standing != null && standing.getWorld().isChunkLoaded(
                            standing.getBlockX() >> 4, standing.getBlockZ() >> 4)
                            && canNavigateTo.test(standing);
                })
                .findFirst()
                .map(point -> {
                    String resourceId = resourceId(villageId, point);
                    ownersByResource.computeIfAbsent(resourceId, ignored -> new HashSet<>()).add(npcUuid);
                    reservationsByNpc.put(npcUuid, new Reservation(resourceId, point));
                    return point;
                })
                .orElse(null);
    }

    ActivityPoint point(UUID npcUuid) {
        Reservation reservation = reservationsByNpc.get(npcUuid);
        return reservation == null ? null : reservation.point();
    }

    void release(UUID npcUuid) {
        Reservation reservation = reservationsByNpc.remove(npcUuid);
        if (reservation == null) return;
        Set<UUID> owners = ownersByResource.get(reservation.resourceId());
        if (owners != null && owners.remove(npcUuid) && owners.isEmpty()) {
            ownersByResource.remove(reservation.resourceId());
        }
    }

    void releasePoint(String villageId, String pointId) {
        String resourceId = villageId + ":activity:" + pointId;
        for (UUID owner : Set.copyOf(ownersByResource.getOrDefault(resourceId, Set.of()))) {
            release(owner);
        }
    }

    void shutdown() {
        ownersByResource.clear();
        reservationsByNpc.clear();
    }

    private double distanceSquared(ActivityPoint point, Location from) {
        Location standing = point.standing().resolve();
        return standing == null ? Double.MAX_VALUE : standing.distanceSquared(from);
    }

    private String resourceId(String villageId, ActivityPoint point) {
        return villageId + ":" + point.resourceId();
    }
}
