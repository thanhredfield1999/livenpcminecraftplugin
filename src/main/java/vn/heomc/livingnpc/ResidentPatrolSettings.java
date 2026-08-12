package vn.heomc.livingnpc;

record ResidentPatrolSettings(
        boolean enabled,
        long scanIntervalTicks,
        int scanRadius,
        int verticalRange,
        int maxCachedPaths,
        int scanBlocksPerTick,
        int minTargetDistance,
        int maxTargetDistance,
        long tripCooldownMinTicks,
        long tripCooldownMaxTicks) {
}
