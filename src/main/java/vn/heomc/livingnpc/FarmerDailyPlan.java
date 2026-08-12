package vn.heomc.livingnpc;

final class FarmerDailyPlan {
    private static final long DAY_TICKS = 24000L;

    private FarmerDailyPlan() {
    }

    static boolean isLunchBreak(long worldTime, ResidentSchedule schedule, FarmerDailyPlanSettings settings) {
        if (!settings.enabled() || settings.lunchDurationTicks() <= 0L) {
            return false;
        }
        long shiftDuration = Math.floorMod(schedule.endTick() - schedule.startTick(), DAY_TICKS);
        if (shiftDuration == 0L || settings.lunchDurationTicks() >= shiftDuration) {
            return false;
        }
        long elapsed = Math.floorMod(worldTime - schedule.startTick(), DAY_TICKS);
        if (elapsed >= shiftDuration) {
            return false;
        }
        long lunchStart = Math.max(0L, (shiftDuration - settings.lunchDurationTicks()) / 2L);
        return elapsed >= lunchStart && elapsed < lunchStart + settings.lunchDurationTicks();
    }
}
