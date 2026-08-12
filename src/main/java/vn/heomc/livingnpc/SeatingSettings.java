package vn.heomc.livingnpc;

record SeatingSettings(
        boolean enabled,
        long restDurationMinTicks,
        long restDurationMaxTicks,
        long standDurationTicks) {
}
