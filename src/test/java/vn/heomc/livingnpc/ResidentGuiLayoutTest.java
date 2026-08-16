package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResidentGuiLayoutTest {
    @Test
    void workZoneControlsUseUniqueSlotsInsideFourRowChest() {
        Set<Integer> slots = new HashSet<>();
        Arrays.stream(ResidentGui.WORK_ZONE_JOB_SLOTS).forEach(slot -> assertTrue(slots.add(slot)));
        for (int slot : new int[]{
                ResidentGui.WORK_ZONE_FISHING_SLOT,
                ResidentGui.WORK_ZONE_RANCH_SLOT,
                ResidentGui.WORK_ZONE_MARKET_SLOT,
                ResidentGui.WORK_ZONE_MINING_SLOT,
                ResidentGui.WORK_ZONE_SCENIC_SLOT,
                ResidentGui.WORK_ZONE_GATE_SLOT,
                ResidentGui.WORK_ZONE_VISITORS_SLOT,
                ResidentGui.WORK_ZONE_NAVIGATION_GATES_SLOT,
                ResidentGui.WORK_ZONE_SEATS_SLOT,
                ResidentGui.WORK_ZONE_BACK_SLOT}) {
            assertTrue(slots.add(slot));
        }

        assertEquals(17, slots.size());
        assertTrue(slots.stream().allMatch(slot -> slot >= 0 && slot < 45));
    }

    @Test
    void roleControlsHaveOneUniqueSlotPerVisibleJob() {
        Set<Integer> slots = new HashSet<>();
        Arrays.stream(ResidentGui.ROLE_JOB_SLOTS).forEach(slot -> assertTrue(slots.add(slot)));

        assertEquals(9, slots.size());
        assertTrue(slots.stream().allMatch(slot -> slot >= 9 && slot <= 17));
    }

    @Test
    void roleActivityUsesOneCombinedDetailSlot() {
        assertEquals(22, ResidentGui.ROLE_ACTIVITY_SLOT);
    }
}
