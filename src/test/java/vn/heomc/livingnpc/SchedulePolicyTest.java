package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchedulePolicyTest {
    @Test
    void worksDuringConfiguredDayWindow() {
        assertFalse(SchedulePolicy.isWorkTime(999, false, 1000, 12000));
        assertTrue(SchedulePolicy.isWorkTime(1000, false, 1000, 12000));
        assertTrue(SchedulePolicy.isWorkTime(11999, false, 1000, 12000));
        assertFalse(SchedulePolicy.isWorkTime(12000, false, 1000, 12000));
    }

    @Test
    void staysHomeDuringStorm() {
        assertFalse(SchedulePolicy.isWorkTime(6000, true, 1000, 12000));
    }

    @Test
    void supportsWindowsAcrossMidnight() {
        assertTrue(SchedulePolicy.isWorkTime(23000, false, 22000, 2000));
        assertTrue(SchedulePolicy.isWorkTime(1000, false, 22000, 2000));
        assertFalse(SchedulePolicy.isWorkTime(12000, false, 22000, 2000));
    }

    @Test
    void identifiesTheLastCompletedShift() {
        assertTrue(SchedulePolicy.completedShiftKey(12000, 1000, 12000) == 0L);
        assertTrue(SchedulePolicy.completedShiftKey(25000, 1000, 12000) == 0L);
        assertTrue(SchedulePolicy.completedShiftKey(27000, 22000, 2000) == 0L);
        assertTrue(SchedulePolicy.activeShiftKey(23000, 22000, 2000) == 0L);
        assertTrue(SchedulePolicy.activeShiftKey(25000, 22000, 2000) == 0L);
    }
}
