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
        java.util.List<RanchPen> ranchPens,
        java.util.List<SeatDefinition> seats,
        java.util.List<MerchantStall> merchantStalls,
        java.util.List<MiningZone> miningZones,
        java.util.List<ActivityPoint> activityPoints) {
    VillageDefinition {
        deliveryLocations = deliveryLocations == null ? java.util.List.of() : java.util.List.copyOf(deliveryLocations);
        workZones = workZones == null ? java.util.Map.of() : java.util.Map.copyOf(workZones);
        ranchPens = ranchPens == null ? java.util.List.of() : java.util.List.copyOf(ranchPens);
        seats = seats == null ? java.util.List.of() : java.util.List.copyOf(seats);
        merchantStalls = merchantStalls == null ? java.util.List.of() : java.util.List.copyOf(merchantStalls);
        miningZones = miningZones == null ? java.util.List.of() : java.util.List.copyOf(miningZones);
        activityPoints = activityPoints == null ? java.util.List.of() : java.util.List.copyOf(activityPoints);
        ranchAnimalLimit = Math.clamp(ranchAnimalLimit, 2, 64);
    }

    VillageDefinition(String id, String name, StoredLocation center, StoredLocation deliveryChest,
                      StoredLocation marketPoint, StoredLocation scenicPoint) {
        this(id, name, center,
                deliveryChest == null ? java.util.List.of() : java.util.List.of(deliveryChest),
                marketPoint, scenicPoint, null, 8, java.util.Map.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    VillageDefinition(String id, String name, StoredLocation center, StoredLocation deliveryChest,
                      StoredLocation marketPoint, StoredLocation scenicPoint,
                      java.util.Map<VillageWorkZoneType, StoredLocation> workZones) {
        this(id, name, center,
                deliveryChest == null ? java.util.List.of() : java.util.List.of(deliveryChest),
                marketPoint, scenicPoint, null, 8, workZones, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
    VillageDefinition withDeliveryChest(StoredLocation chest) {
        java.util.ArrayList<StoredLocation> updated = new java.util.ArrayList<>(deliveryLocations);
        boolean duplicate = updated.stream().anyMatch(location -> sameBlock(location, chest));
        if (!duplicate) updated.add(chest);
        return copy(updated, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, merchantStalls);
    }

    VillageDefinition withoutDeliveryLocation(int index) {
        if (index < 0 || index >= deliveryLocations.size()) return this;
        java.util.ArrayList<StoredLocation> updated = new java.util.ArrayList<>(deliveryLocations);
        updated.remove(index);
        return copy(updated, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, merchantStalls);
    }

    StoredLocation deliveryChest() {
        return deliveryLocations.isEmpty() ? null : deliveryLocations.getFirst();
    }

    VillageDefinition withSocialPoint(String type, StoredLocation point) {
        return switch (type) {
            case "cho" -> copy(deliveryLocations, point, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, merchantStalls);
            case "ngamcanh" -> copy(deliveryLocations, marketPoint, point, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, merchantStalls);
            default -> this;
        };
    }

    VillageDefinition withWorkZone(VillageWorkZoneType type, StoredLocation center) {
        java.util.EnumMap<VillageWorkZoneType, StoredLocation> updated =
                new java.util.EnumMap<>(VillageWorkZoneType.class);
        updated.putAll(workZones);
        updated.put(type, center);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, updated, ranchPens, seats, merchantStalls);
    }

    StoredLocation workZone(VillageWorkZoneType type) {
        if (type == VillageWorkZoneType.RANCH && !ranchPens.isEmpty()) return ranchPens.getFirst().center();
        return workZones.get(type);
    }

    VillageDefinition withRanchPen(RanchPen pen) {
        if (ranchPens.size() >= 9 || ranchPens.stream().anyMatch(existing -> sameBlock(existing.center(), pen.center()))) {
            return this;
        }
        java.util.ArrayList<RanchPen> updated = new java.util.ArrayList<>(ranchPens);
        updated.add(pen);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, updated, seats, merchantStalls);
    }

    VillageDefinition withoutRanchPen(String penId) {
        java.util.ArrayList<RanchPen> updated = new java.util.ArrayList<>(ranchPens);
        if (!updated.removeIf(pen -> pen.id().equals(penId))) return this;
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, updated, seats, merchantStalls);
    }

    VillageDefinition withVisitorGate(StoredLocation gate) {
        return copy(deliveryLocations, marketPoint, scenicPoint, gate, ranchAnimalLimit, workZones, ranchPens, seats, merchantStalls);
    }

    VillageDefinition withRanchAnimalLimit(int limit) {
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, limit, workZones, ranchPens, seats, merchantStalls);
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
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, updated, merchantStalls);
    }

    VillageDefinition withoutSeat(String seatId) {
        java.util.ArrayList<SeatDefinition> updated = new java.util.ArrayList<>(seats);
        if (!updated.removeIf(seat -> seat.id().equals(seatId))) return this;
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, updated, merchantStalls);
    }

    MerchantStall merchantStall(java.util.UUID merchantUuid) {
        return merchantStalls.stream().filter(stall -> stall.merchantUuid().equals(merchantUuid)).findFirst().orElse(null);
    }

    VillageDefinition withMerchantStall(MerchantStall stall) {
        java.util.ArrayList<MerchantStall> updated = new java.util.ArrayList<>(merchantStalls);
        updated.removeIf(existing -> existing.merchantUuid().equals(stall.merchantUuid()));
        updated.add(stall);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, updated);
    }

    VillageDefinition withoutMerchantStall(java.util.UUID merchantUuid) {
        java.util.ArrayList<MerchantStall> updated = new java.util.ArrayList<>(merchantStalls);
        if (!updated.removeIf(stall -> stall.merchantUuid().equals(merchantUuid))) return this;
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, updated);
    }

    VillageDefinition withMiningZone(MiningZone zone) {
        if (miningZones.size() >= 16 || miningZones.stream().anyMatch(existing -> existing.overlaps(zone))) return this;
        java.util.ArrayList<MiningZone> updated = new java.util.ArrayList<>(miningZones);
        updated.add(zone);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, merchantStalls, updated);
    }

    VillageDefinition withoutMiningZone(String zoneId) {
        java.util.ArrayList<MiningZone> updated = new java.util.ArrayList<>(miningZones);
        if (!updated.removeIf(zone -> zone.id().equals(zoneId))) return this;
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit, workZones, ranchPens, seats, merchantStalls, updated);
    }

    VillageDefinition withActivityPoint(ActivityPoint point) {
        java.util.ArrayList<ActivityPoint> updated = new java.util.ArrayList<>(activityPoints);
        updated.removeIf(existing -> existing.id().equals(point.id()));
        updated.add(point);
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit,
                workZones, ranchPens, seats, merchantStalls, miningZones, updated);
    }

    VillageDefinition withoutActivityPoint(String pointId) {
        java.util.ArrayList<ActivityPoint> updated = new java.util.ArrayList<>(activityPoints);
        if (!updated.removeIf(point -> point.id().equals(pointId))) return this;
        return copy(deliveryLocations, marketPoint, scenicPoint, visitorGate, ranchAnimalLimit,
                workZones, ranchPens, seats, merchantStalls, miningZones, updated);
    }

    private VillageDefinition copy(
            java.util.List<StoredLocation> deliveries, StoredLocation market, StoredLocation scenic,
            StoredLocation gate, int animalLimit, java.util.Map<VillageWorkZoneType, StoredLocation> zones,
            java.util.List<RanchPen> pens,
             java.util.List<SeatDefinition> updatedSeats, java.util.List<MerchantStall> stalls) {
        return copy(deliveries, market, scenic, gate, animalLimit, zones, pens, updatedSeats, stalls, miningZones, activityPoints);
    }

    private VillageDefinition copy(
            java.util.List<StoredLocation> deliveries, StoredLocation market, StoredLocation scenic,
            StoredLocation gate, int animalLimit, java.util.Map<VillageWorkZoneType, StoredLocation> zones,
            java.util.List<RanchPen> pens, java.util.List<SeatDefinition> updatedSeats,
             java.util.List<MerchantStall> stalls, java.util.List<MiningZone> mines) {
        return copy(deliveries, market, scenic, gate, animalLimit, zones, pens, updatedSeats, stalls, mines, activityPoints);
    }

    private VillageDefinition copy(
            java.util.List<StoredLocation> deliveries, StoredLocation market, StoredLocation scenic,
            StoredLocation gate, int animalLimit, java.util.Map<VillageWorkZoneType, StoredLocation> zones,
            java.util.List<RanchPen> pens, java.util.List<SeatDefinition> updatedSeats,
            java.util.List<MerchantStall> stalls, java.util.List<MiningZone> mines,
            java.util.List<ActivityPoint> points) {
        return new VillageDefinition(id, name, center, deliveries, market, scenic, gate, animalLimit,
                zones, pens, updatedSeats, stalls, mines, points);
    }

    private boolean sameBlock(StoredLocation first, StoredLocation second) {
        return first.world().equals(second.world())
                && (int) Math.floor(first.x()) == (int) Math.floor(second.x())
                && (int) Math.floor(first.y()) == (int) Math.floor(second.y())
                && (int) Math.floor(first.z()) == (int) Math.floor(second.z());
    }
}
