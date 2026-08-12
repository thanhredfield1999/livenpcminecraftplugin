package vn.heomc.livingnpc;

record FisherSettings(
        long attemptDelayMinTicks,
        long attemptDelayMaxTicks,
        double successChance,
        int maxCatchPerShift,
        int waterSearchRadius,
        int waterSearchVerticalRange) {
}
