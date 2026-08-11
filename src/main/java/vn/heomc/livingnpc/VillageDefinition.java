package vn.heomc.livingnpc;

record VillageDefinition(
        String id,
        String name,
        StoredLocation center,
        StoredLocation deliveryChest,
        StoredLocation marketPoint,
        StoredLocation scenicPoint) {
    VillageDefinition withDeliveryChest(StoredLocation chest) {
        return new VillageDefinition(id, name, center, chest, marketPoint, scenicPoint);
    }

    VillageDefinition withSocialPoint(String type, StoredLocation point) {
        return switch (type) {
            case "cho" -> new VillageDefinition(id, name, center, deliveryChest, point, scenicPoint);
            case "ngamcanh" -> new VillageDefinition(id, name, center, deliveryChest, marketPoint, point);
            default -> this;
        };
    }
}
