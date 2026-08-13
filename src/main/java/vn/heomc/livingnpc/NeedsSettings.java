package vn.heomc.livingnpc;

record NeedsSettings(
        boolean enabled,
        long hungerDecayTicksPerPoint,
        long thirstDecayTicksPerPoint,
        long maxManagedDeltaTicks,
        long saveIntervalTicks) {
}
