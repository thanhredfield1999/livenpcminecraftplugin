package vn.heomc.livingnpc;

record VillageDefinition(
        String id,
        String name,
        StoredLocation center,
        java.util.List<StoredLocation> deliveryLocations,
        StoredLocation marketPoint,
        StoredLocation scenicPoint,
        StoredLocation visitorGate,
        int ranchAnimalLimit,
        java.util.Map<VillageWorkZoneType, StoredLocation> workZones,
        java.util.List<SeatDefinition> seats,
        java.util.List<MerchantStall> merchantStalls) {
    VillageDefinition {
        deliveryLocations = deliveryLocations == null ? java.util.List.of() : java.util.List.copyOf(deliveryLocations);
        workZones = workZones == null ? java.util.Map.of() : java.util.Map.copyOf(workZones);
        seats = seats == null ? java.util.List.of() : java.util.List.copyOf(seats);
        merchantStalls = merchantStalls == null ? java.util.List.of() : java.util.List.copyOf(merchantStalls);
        ranchAnimalLimit = Math.clamp(ranchAnimalLimit, 2, 64);
    }

    VillageDefinition(String id, String name, StoredLocation center, StoredLocation deliveryChest,
                      StoredLocation marketPoint, StoredLocation scenicPoint) {
        this(id, name, center,
                deliveryChest == null ? java.util.List.of() : java.util.List.of(deliveryChest),
                marketPoint, scenicPoint, null, 8, java.util.Map.of(), java.util.List.of(), java.util.List.of());
    }

    VillageDefinition(String id, String name, StoredLocation center, StoredLocation deliveryChest,
                      StoredLocation marketPoint, StoredLocation scenicPoint,
                      java.util.Map<VillageWorkZoneType, StoredLocation> workZones) {
        this(id, name, center,
                deliveryChest == null ? java.util.List.of() : java.util.List.of(deliveryChest),
                marketPoint, scenicPoint, null, 8, workZones, java.util.List.of(), java.util.List.of());
    }
    VillageDefinition withDeliveryChest(StoredLocation chest) {
        java.util.ArrayList<StoredLocation> updated = new java.util.ArrayList<>(deliveryLocations);
        boolean duplicate = updated.stream().anyMatch(location -> sameBlock(location, chest));
        if (!duplicate) updated.add(chest);
        return copy(updated, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, seats, merchantStalls);
    }

    VillageDefinition withoutDeliveryLocation(int index) {
        if (index < 0 || index >= deliveryLocations.size()) return this;
        java.util.ArrayList<StoredLocation> updated = new java.util.ArrayList<>(deliveryLocations);
        updated.remove(index);
        return copy(updated, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, seats, merchantStalls);
    }

    StoredLocation deliveryChest() {
        return deliveryLocations.isEmpty() ? null : deliveryLocations.getFirst();
    }

    VillageDefinition withSocialPoint(String type, StoredLocation point) {
        return switch (type) {
            case "cho" -> copy(deliveryLocations, point, scenicPoint, visitorGate, ranchAnimalLimit, workZones, seats, merchantStalls);
            case "ngamcanh" -> copy(deliveryLocations, marketPoint, point, visitorGate, ranchAnimalLimit, workZones, seats, merchantStalls);
            default -> this;
        };
    }

    VillageDefinition withWorkZone(VillageWorkZoneType type, StoredLocation center) {
        java.util.EnumMap<VillageWorkZoneType, StoredLocation> updated =
                new java.util.EnumMap<>(VillageWorkZoneType.class);
        updated.putAll(workZones);
        updated.put(type, center);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, updated, seats, merchantStalls);
    }

    StoredLocation workZone(VillageWorkZoneType type) {
        return workZones.get(type);
    }

    VillageDefinition withVisitorGate(StoredLocation gate) {
        return copy(deliveryLocations, marketPoint, scenicPoint, gate, ranchAnimalLimit, workZones, seats, merchantStalls);
    }

    VillageDefinition withRanchAnimalLimit(int limit) {
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, limit, workZones, seats, merchantStalls);
    }

    VillageDefinition withSeat(SeatDefinition seat) {
        java.util.ArrayList<SeatDefinition> updated = new java.util.ArrayList<>(seats);
        int existingIndex = -1;
        for (int index = 0; index < updated.size(); index++) {
            if (sameBlock(updated.get(index).location(), seat.location())) {
                existingIndex = index;
                break;
            }
        }
        SeatDefinition stored = existingIndex < 0
                ? seat
                : new SeatDefinition(updated.get(existingIndex).id(), seat.location(), seat.type());
        if (existingIndex < 0) updated.add(stored);
        else updated.set(existingIndex, stored);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, updated, merchantStalls);
    }

    VillageDefinition withoutSeat(String seatId) {
        java.util.ArrayList<SeatDefinition> updated = new java.util.ArrayList<>(seats);
        if (!updated.removeIf(seat -> seat.id().equals(seatId))) return this;
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, updated, merchantStalls);
    }

    MerchantStall merchantStall(java.util.UUID merchantUuid) {
        return merchantStalls.stream().filter(stall -> stall.merchantUuid().equals(merchantUuid)).findFirst().orElse(null);
    }

    VillageDefinition withMerchantStall(MerchantStall stall) {
        java.util.ArrayList<MerchantStall> updated = new java.util.ArrayList<>(merchantStalls);
        updated.removeIf(existing -> existing.merchantUuid().equals(stall.merchantUuid()));
        updated.add(stall);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, seats, updated);
    }

    VillageDefinition withoutMerchantStall(java.util.UUID merchantUuid) {
        java.util.ArrayList<MerchantStall> updated = new java.util.ArrayList<>(merchantStalls);
        if (!updated.removeIf(stall -> stall.merchantUuid().equals(merchantUuid))) return this;
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, seats, updated);
    }

    private VillageDefinition copy(
            java.util.List<StoredLocation> deliveries, StoredLocation market, StoredLocation scenic,
            StoredLocation gate, int animalLimit, java.util.Map<VillageWorkZoneType, StoredLocation> zones,
            java.util.List<SeatDefinition> updatedSeats, java.util.List<MerchantStall> stalls) {
        return new VillageDefinition(id, name, center, deliveries, market, scenic, gate, animalLimit, zones, updatedSeats, stalls);
    }

    private boolean sameBlock(StoredLocation first, StoredLocation second) {
        return first.world().equals(second.world())
                && (int) Math.floor(first.x()) == (int) Math.floor(second.x())
                && (int) Math.floor(first.y()) == (int) Math.floor(second.y())
                && (int) Math.floor(first.z()) == (int) Math.floor(second.z());
    }
}
