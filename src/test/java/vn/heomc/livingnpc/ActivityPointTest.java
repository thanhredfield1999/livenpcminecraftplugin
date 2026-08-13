package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityPointTest {
    private static final StoredLocation LOCATION =
            new StoredLocation("StillCliff", 10.5, 64, 10.5, 90, 0);

    @Test
    void appliesAssignmentRoleAndOvernightOpeningHours() {
        UUID assigned = UUID.randomUUID();
        ActivityPoint point = new ActivityPoint(
                "home_exit_1", ActivityPointType.HOME_EXIT, LOCATION, LOCATION, 1,
                18_000L, 2_000L, Set.of(ResidentRole.RESIDENT), assigned);

        assertTrue(point.availableAt(19_000L, ResidentRole.RESIDENT, assigned));
        assertTrue(point.availableAt(1_000L, ResidentRole.RESIDENT, assigned));
        assertFalse(point.availableAt(6_000L, ResidentRole.RESIDENT, assigned));
        assertFalse(point.availableAt(1_000L, ResidentRole.FARMER, assigned));
        assertFalse(point.availableAt(1_000L, ResidentRole.RESIDENT, UUID.randomUUID()));
        assertEquals("activity:home_exit_1", point.resourceId());
    }

    @Test
    void boundsCapacityAndTreatsMissingHoursAndRolesAsOpen() {
        ActivityPoint point = new ActivityPoint(
                "social_1", ActivityPointType.SOCIAL, LOCATION, LOCATION, 100,
                null, null, null, null);

        assertEquals(64, point.capacity());
        assertTrue(point.availableAt(12_000L, ResidentRole.FARMER, UUID.randomUUID()));
    }
}
