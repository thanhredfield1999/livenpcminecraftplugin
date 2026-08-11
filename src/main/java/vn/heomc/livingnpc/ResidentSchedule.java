package vn.heomc.livingnpc;

record ResidentSchedule(long startTick, long endTick) {
    ResidentSchedule {
        startTick = Math.floorMod(startTick, 24000L);
        endTick = Math.floorMod(endTick, 24000L);
    }
}
