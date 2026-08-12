package vn.heomc.livingnpc;

record RancherSettings(
        long scanIntervalTicks,
        long actionCooldownTicks,
        int loveModeTicks,
        int maxCullPerCycle,
        double interactionRange,
        int escapeSearchRadius,
        long patrolIntervalTicks) {
}
