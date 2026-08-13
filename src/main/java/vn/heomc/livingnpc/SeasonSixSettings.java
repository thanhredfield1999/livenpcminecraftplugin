package vn.heomc.livingnpc;

record SeasonSixSettings(boolean enabled, long morningExitTimeoutTicks) {
    SeasonSixSettings {
        morningExitTimeoutTicks = Math.clamp(morningExitTimeoutTicks, 100L, 1200L);
    }
}
