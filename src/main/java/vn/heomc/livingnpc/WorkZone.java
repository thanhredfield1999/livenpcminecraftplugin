package vn.heomc.livingnpc;

record WorkZone(StoredLocation center, int horizontalRadius, int verticalRange, TargetMode mode) {
    WorkZone {
        if (horizontalRadius < 0 || verticalRange < 0) {
            throw new IllegalArgumentException("Work zone ranges must be non-negative");
        }
    }

    boolean contains(String world, int x, int y, int z) {
        if (!center.world().equals(world)) {
            return false;
        }
        return Math.abs(x - Math.floor(center.x())) <= horizontalRadius
                && Math.abs(z - Math.floor(center.z())) <= horizontalRadius
                && Math.abs(y - Math.floor(center.y())) <= verticalRange;
    }
}
