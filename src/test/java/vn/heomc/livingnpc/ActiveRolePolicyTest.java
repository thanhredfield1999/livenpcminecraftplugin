package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActiveRolePolicyTest {
    private static final ResidentSchedule DEFAULT = new ResidentSchedule(1000, 12000);

    @Test
    void switchesToTheOnlyRoleCurrentlyScheduled() {
        ResidentRole selected = ActiveRolePolicy.select(
                EnumSet.of(ResidentRole.FARMER, ResidentRole.FISHER),
                ResidentRole.FARMER,
                Map.of(
                        ResidentRole.FARMER, new ResidentSchedule(1000, 12000),
                        ResidentRole.FISHER, new ResidentSchedule(12000, 22000)),
                DEFAULT,
                12000);

        assertEquals(ResidentRole.FISHER, selected);
    }

    @Test
    void retainsCurrentRoleWhenSchedulesOverlap() {
        ResidentRole selected = ActiveRolePolicy.select(
                EnumSet.of(ResidentRole.FARMER, ResidentRole.FISHER),
                ResidentRole.FISHER,
                Map.of(
                        ResidentRole.FARMER, new ResidentSchedule(1000, 12000),
                        ResidentRole.FISHER, new ResidentSchedule(6000, 18000)),
                DEFAULT,
                8000);

        assertEquals(ResidentRole.FISHER, selected);
    }

    @Test
    void keepsCurrentRoleWhenNoAssignedRoleIsOnShift() {
        ResidentRole selected = ActiveRolePolicy.select(
                EnumSet.of(ResidentRole.FARMER, ResidentRole.FISHER),
                ResidentRole.FARMER,
                Map.of(
                        ResidentRole.FARMER, new ResidentSchedule(1000, 12000),
                        ResidentRole.FISHER, new ResidentSchedule(12000, 18000)),
                DEFAULT,
                23000);

        assertEquals(ResidentRole.FARMER, selected);
    }

    @Test
    void supportsRoleSchedulesAcrossMidnight() {
        ResidentRole selected = ActiveRolePolicy.select(
                EnumSet.of(ResidentRole.FARMER, ResidentRole.SECURITY),
                ResidentRole.FARMER,
                Map.of(
                        ResidentRole.FARMER, new ResidentSchedule(1000, 12000),
                        ResidentRole.SECURITY, new ResidentSchedule(22000, 2000)),
                DEFAULT,
                500);

        assertEquals(ResidentRole.SECURITY, selected);
    }
}
