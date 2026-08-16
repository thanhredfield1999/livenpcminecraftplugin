package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FarmerActivityResolverTest {
    @Test
    void farmerUsesTheSharedResidentRuntimePhase() {
        assertEquals(FarmerPhase.GOING_TO_CROP, FarmerActivityResolver.resolvePhase(
                ResidentRole.FARMER,
                FarmerPhase.GOING_TO_CROP,
                null, null, null));
    }

    @Test
    void sleepPhasesOverrideTheActiveRoleRuntime() {
        assertEquals(FarmerPhase.SLEEPING, FarmerActivityResolver.resolvePhase(
                ResidentRole.FISHER,
                FarmerPhase.SLEEPING,
                FarmerPhase.CASTING_LINE, null, null));
        assertEquals(FarmerPhase.GOING_TO_BED, FarmerActivityResolver.resolvePhase(
                ResidentRole.COOK,
                FarmerPhase.GOING_TO_BED,
                null, null, FarmerPhase.PRODUCING));
    }

    @Test
    void fisherUsesItsOwnRuntimePhase() {
        assertEquals(FarmerPhase.GOING_TO_FISHING_SPOT, FarmerActivityResolver.resolvePhase(
                ResidentRole.FISHER,
                FarmerPhase.INACTIVE,
                FarmerPhase.GOING_TO_FISHING_SPOT, null, null));
    }

    @Test
    void merchantUsesItsOwnRuntimePhase() {
        assertEquals(FarmerPhase.SERVING, FarmerActivityResolver.resolvePhase(
                ResidentRole.MERCHANT,
                FarmerPhase.INACTIVE,
                null, FarmerPhase.SERVING, null));
    }

    @Test
    void civilRoleUsesItsOwnRuntimePhase() {
        assertEquals(FarmerPhase.PRODUCING, FarmerActivityResolver.resolvePhase(
                ResidentRole.COOK,
                FarmerPhase.INACTIVE,
                null, null, FarmerPhase.PRODUCING));
    }

    @Test
    void missingRoleRuntimeFallsBackToTheSharedPhase() {
        assertEquals(FarmerPhase.INACTIVE, FarmerActivityResolver.resolvePhase(
                ResidentRole.FISHER,
                FarmerPhase.INACTIVE,
                null, null, null));
    }

    @Test
    void movingFarmerSaysItIsWalkingNotInspectingOrIdle() {
        assertTrue(FarmerActivityResolver.describeActivity(FarmerPhase.GOING_TO_PLOT)
                .contains("đi"));
        assertTrue(FarmerActivityResolver.describeActivity(FarmerPhase.GOING_TO_CROP)
                .contains("đi"));
        assertEquals("Đang đi tới cây trồng",
                FarmerActivityResolver.describeActivity(FarmerPhase.GOING_TO_CROP));
        assertEquals("Đang đi tới ruộng",
                FarmerActivityResolver.describeActivity(FarmerPhase.GOING_TO_PLOT));
        assertEquals("Đang kiểm tra cây",
                FarmerActivityResolver.describeActivity(FarmerPhase.INSPECTING));
        assertEquals("Đang nghỉ hoặc chờ ca",
                FarmerActivityResolver.describeActivity(FarmerPhase.INACTIVE));
    }
}
