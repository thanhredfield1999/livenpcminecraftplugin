package vn.heomc.livingnpc;

record MinerSettings(
        long scanIntervalTicks,
        long breakDelayTicks,
        long swingIntervalTicks,
        long restorationDelaySeconds,
        int batchSize) {
}
