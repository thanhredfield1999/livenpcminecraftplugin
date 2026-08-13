package vn.heomc.livingnpc;

record MiningZone(String id, StoredLocation corner, int minY, int maxY) {
    MiningZone {
        if (minY > maxY) throw new IllegalArgumentException("minY > maxY");
    }

    boolean contains(String world, int x, int y, int z) {
        int cornerX = (int) Math.floor(corner.x());
        int cornerZ = (int) Math.floor(corner.z());
        return corner.world().equals(world) && x >= cornerX && x <= cornerX + 1
                && z >= cornerZ && z <= cornerZ + 1 && y >= minY && y <= maxY;
    }

    boolean overlaps(MiningZone other) {
        if (!corner.world().equals(other.corner.world())) return false;
        int x = (int) Math.floor(corner.x());
        int z = (int) Math.floor(corner.z());
        int otherX = (int) Math.floor(other.corner.x());
        int otherZ = (int) Math.floor(other.corner.z());
        return x <= otherX + 1 && x + 1 >= otherX && z <= otherZ + 1 && z + 1 >= otherZ
                && minY <= other.maxY && maxY >= other.minY;
    }
}
