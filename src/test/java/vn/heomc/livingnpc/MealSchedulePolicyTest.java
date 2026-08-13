package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MealSchedulePolicyTest {
    @Test
    void keepsFeatureClosedByDefault() {
        assertNull(MealSchedulePolicy.current(1000L, settings(false)));
    }

    @Test
    void selectsEachMealOnceFromWorldTime() {
        SeasonTenSettings settings = settings(true);

        assertEquals(MealPeriod.BREAKFAST, MealSchedulePolicy.current(500L, settings).period());
        assertEquals(MealPeriod.LUNCH, MealSchedulePolicy.current(6000L, settings).period());
        assertEquals(MealPeriod.DINNER, MealSchedulePolicy.current(11_000L, settings).period());
        assertNull(MealSchedulePolicy.current(13_000L, settings));
    }

    @Test
    void assignsAfterMidnightPartOfCrossDayWindowToPreviousDay() {
        SeasonTenSettings settings = new SeasonTenSettings(
                true, 23_000L, 1000L, 5000L, 6000L, 10_000L, 11_000L, 2, 8, 0, true);

        MealOpportunity beforeMidnight = MealSchedulePolicy.current(23_500L, settings);
        MealOpportunity afterMidnight = MealSchedulePolicy.current(24_500L, settings);

        assertEquals(beforeMidnight.id(), afterMidnight.id());
        assertEquals("0:breakfast", afterMidnight.id());
    }

    private SeasonTenSettings settings(boolean enabled) {
        return new SeasonTenSettings(
                enabled, 500L, 2500L, 5500L, 7500L, 10_500L, 12_500L, 2, 8, 0, true);
    }
}
