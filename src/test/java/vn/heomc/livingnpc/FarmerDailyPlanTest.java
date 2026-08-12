package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FarmerDailyPlanTest {
    private static final ResidentSchedule DAY_SHIFT = new ResidentSchedule(1000L, 12000L);

    @Test
    void takesLunchAtTheMiddleOfADayShift() {
        FarmerDailyPlanSettings settings = new FarmerDailyPlanSettings(true, 1000L);

        assertFalse(FarmerDailyPlan.isLunchBreak(5999L, DAY_SHIFT, settings));
        assertTrue(FarmerDailyPlan.isLunchBreak(6000L, DAY_SHIFT, settings));
        assertTrue(FarmerDailyPlan.isLunchBreak(6999L, DAY_SHIFT, settings));
        assertFalse(FarmerDailyPlan.isLunchBreak(7000L, DAY_SHIFT, settings));
    }

    @Test
    void doesNotTakeLunchOutsideTheShift() {
        FarmerDailyPlanSettings settings = new FarmerDailyPlanSettings(true, 1000L);

        assertFalse(FarmerDailyPlan.isLunchBreak(999L, DAY_SHIFT, settings));
        assertFalse(FarmerDailyPlan.isLunchBreak(12000L, DAY_SHIFT, settings));
    }

    @Test
    void supportsShiftsAcrossMidnight() {
        ResidentSchedule nightShift = new ResidentSchedule(22000L, 4000L);
        FarmerDailyPlanSettings settings = new FarmerDailyPlanSettings(true, 1000L);

        assertFalse(FarmerDailyPlan.isLunchBreak(499L, nightShift, settings));
        assertTrue(FarmerDailyPlan.isLunchBreak(500L, nightShift, settings));
        assertTrue(FarmerDailyPlan.isLunchBreak(1499L, nightShift, settings));
        assertFalse(FarmerDailyPlan.isLunchBreak(1500L, nightShift, settings));
    }

    @Test
    void disablesInvalidOrConfiguredOffLunchBreaks() {
        assertFalse(FarmerDailyPlan.isLunchBreak(
                6000L, DAY_SHIFT, new FarmerDailyPlanSettings(false, 1000L)));
        assertFalse(FarmerDailyPlan.isLunchBreak(
                6000L, DAY_SHIFT, new FarmerDailyPlanSettings(true, 0L)));
        assertFalse(FarmerDailyPlan.isLunchBreak(
                6000L, DAY_SHIFT, new FarmerDailyPlanSettings(true, 11000L)));
    }
}
