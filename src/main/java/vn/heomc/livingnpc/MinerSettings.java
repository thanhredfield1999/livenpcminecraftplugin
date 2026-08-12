package vn.heomc.livingnpc;

record MinerSettings(
        long scanIntervalTicks,
        long breakDelayTicks,
        long swingIntervalTicks,
        int searchRadius,
        int verticalRange,
        int avoidanceRadius,
        int minimumTravelDistance) {
}
