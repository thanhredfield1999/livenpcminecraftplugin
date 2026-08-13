package vn.heomc.livingnpc;

record KitchenAppliance(String id, KitchenApplianceType type, StoredLocation block) {
    KitchenAppliance {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid appliance id");
        if (type == null || block == null) throw new IllegalArgumentException("Appliance type and block are required");
    }

    String blockKey() {
        return block.world() + ':' + (int) Math.floor(block.x()) + ':'
                + (int) Math.floor(block.y()) + ':' + (int) Math.floor(block.z());
    }
}
