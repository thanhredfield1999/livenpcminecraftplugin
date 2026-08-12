package vn.heomc.livingnpc;

final class SchedulePolicy {
    private SchedulePolicy() {
    }

    static boolean isWorkTime(long worldTime, boolean storming, long startTick, long endTick) {
        return isScheduledTime(worldTime, startTick, endTick) && !storming;
    }

    static boolean isScheduledTime(long worldTime, long startTick, long endTick) {
        long time = Math.floorMod(worldTime, 24000L);
        return startTick <= endTick
                ? time >= startTick && time < endTick
                : time >= startTick || time < endTick;
    }

    static boolean isScheduledTime(long worldTime, ResidentSchedule schedule) {
        return isScheduledTime(worldTime, schedule.startTick(), schedule.endTick());
    }

    static boolean isWorkTime(long worldTime, boolean storming, ResidentSchedule schedule) {
        return isWorkTime(worldTime, storming, schedule.startTick(), schedule.endTick());
    }

    static long completedShiftKey(long fullTime, long startTick, long endTick) {
        long day = Math.floorDiv(fullTime, 24000L);
        long time = Math.floorMod(fullTime, 24000L);
        if (startTick <= endTick) {
            return time >= endTick ? day : day - 1L;
        }
        return day - 1L;
    }

    static long activeShiftKey(long fullTime, long startTick, long endTick) {
        long day = Math.floorDiv(fullTime, 24000L);
        long time = Math.floorMod(fullTime, 24000L);
        if (startTick > endTick && time < endTick) {
            return day - 1L;
        }
        return day;
    }

    static long activeShiftKey(long fullTime, ResidentSchedule schedule) {
        return activeShiftKey(fullTime, schedule.startTick(), schedule.endTick());
    }

    static long completedShiftKey(long fullTime, ResidentSchedule schedule) {
        return completedShiftKey(fullTime, schedule.startTick(), schedule.endTick());
    }
}
