package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GateRouteCoordinatorTest {
    private final World world = Mockito.mock(World.class);

    @Test
    void moiLanStartLegTaoGenerationTangDan() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);

        coordinator.start(location(0, 64, 0), location(10, 64, 0), List.of(gate), 0L);
        assertEquals(List.of(1L), navigation.generations);
        coordinator.tick(gate.approach(), 1L);
        assertEquals(List.of(1L, 2L), navigation.generations);
        assertEquals(GateRoute.Leg.EXIT, coordinator.currentLeg());
    }

    @Test
    void configuredStagingMustCompleteBeforeApproachCanOpenGate() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        GateRoute.Candidate gate = new GateRoute.Candidate(
                "world:5:64:0", location(3.5, 64, 0.5), location(4.5, 64, 0.5), location(6.5, 64, 0.5));

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.start(location(0, 64, 0.5), location(10, 64, 0.5), List.of(gate), 0L));
        assertEquals(List.of(gate.staging()), navigation.targets);
        assertEquals(GateRoute.Leg.STAGING, coordinator.currentLeg());

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.staging(), 1L));
        assertEquals(List.of(gate.staging(), gate.approach()), navigation.targets);
        assertEquals(GateRoute.Leg.APPROACH, coordinator.currentLeg());
        assertEquals(0, navigation.requestCount);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.approach(), 2L));
        assertEquals(List.of(gate.staging(), gate.approach(), gate.exit()), navigation.targets);
        assertEquals(1, navigation.requestCount);
        assertEquals(GateRoute.Leg.EXIT, coordinator.currentLeg());
    }

    @Test
    void timeoutSauMotLanRestartLoaiCandidateKhongTeleportRecovery() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 10L, 0.75, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);

        coordinator.start(location(0, 64, 0), location(10, 64, 0), List.of(gate), 0L);
        coordinator.tick(location(0, 64, 0), 10L);
        assertEquals(GateRouteCoordinator.Result.FAILED,
                coordinator.tick(location(0, 64, 0), 20L));

        assertEquals(0, navigation.recoverCount);
        assertEquals(List.of(gate.approach(), gate.approach()), navigation.targets);
        assertFalse(coordinator.active());
    }

    @Test
    void timeoutThuCandidateMotLanRoiExhaustVaCleanup() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 10L, 0.75, 1.0);
        Location start = location(0, 64, 0);
        Location target = location(20, 64, 0);
        GateRoute.Candidate first = candidate(4, 6);
        GateRoute.Candidate second = candidate(8, 10);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.start(start, target, List.of(first, second), 100L));
        assertEquals(List.of(first.approach()), navigation.targets);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(start, 110L));
        assertEquals(List.of(first.approach(), first.approach()), navigation.targets);
        assertEquals(1, navigation.cancelCount);

        // Exhaust leg 1. Coordinator releases it and starts candidate 2 once.
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(start, 120L));
        assertEquals(List.of(first.approach(), first.approach(), second.approach()), navigation.targets);
        assertEquals(2, navigation.cancelCount);
        assertTrue(coordinator.active());
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(start, 129L));
        assertEquals(2, navigation.cancelCount);
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(start, 130L));
        assertEquals(3, navigation.cancelCount);
        assertEquals(List.of(first.approach(), first.approach(), second.approach(), second.approach()),
                navigation.targets);
        assertTrue(coordinator.active());
    }

    @Test
    void exhaustedLegRetryFailsInsteadOfRetryingSameCandidateForever() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 10L, 0.75, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.start(location(0, 64, 0), location(20, 64, 0), List.of(gate), 0L));

        // Một restart bounded. Lần kế tiếp phải kết thúc candidate, không cooldown/retry vô hạn.
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(0, 64, 0), 10L));
        assertEquals(GateRouteCoordinator.Result.FAILED,
                coordinator.tick(location(0, 64, 0), 20L));
        assertFalse(coordinator.active());
        assertEquals(List.of(gate.approach(), gate.approach()), navigation.targets);

        assertEquals(GateRouteCoordinator.Result.IDLE,
                coordinator.tick(location(0, 64, 0), 29L));
        assertEquals(List.of(gate.approach(), gate.approach()), navigation.targets);
    }

    @Test
    void deadlineGanLongMaxKhongTimeoutNgaySauKhiBatDau() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 10L, 0.75, 1.0);
        long startTick = Long.MAX_VALUE - 5L;

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.start(location(0, 64, 0), location(10, 64, 0),
                        List.of(candidate(4, 6)), startTick));

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(0, 64, 0), startTick + 1L));
        assertEquals(0, navigation.cancelCount);
        assertTrue(coordinator.active());

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(0, 64, 0), startTick + 9L));
        assertEquals(0, navigation.cancelCount);
        assertTrue(coordinator.active());

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(0, 64, 0), startTick + 10L));
        assertEquals(1, navigation.cancelCount);
        assertTrue(coordinator.active());
    }

    @Test
    void startTuChoiViTriKhongHuuHanVaCandidateNull() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 10L, 0.75, 1.0);
        Location start = location(0, 64, 0);
        Location target = location(20, 64, 0);

        assertEquals(GateRouteCoordinator.Result.FAILED,
                coordinator.start(new Location(world, Double.POSITIVE_INFINITY, 64, 0), target,
                        List.of(candidate(4, 6)), 0L));
        assertEquals(GateRouteCoordinator.Result.FAILED,
                coordinator.start(start, target, java.util.Arrays.asList((GateRoute.Candidate) null), 0L));
        assertFalse(coordinator.active());
    }

    @Test
    void gioiHanKhoangCachKhongHuuHanBiTuChoi() {
        FakeNavigation navigation = new FakeNavigation();

        for (double invalid : List.of(
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GateRouteCoordinator(navigation, 10L, invalid, 1.0));
            assertThrows(IllegalArgumentException.class,
                    () -> new GateRouteCoordinator(navigation, 10L, 0.75, invalid));
        }
    }

    @Test
    void chiViTriThatMoiChuyenLegVaCompleteCleanup() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);
        Location target = location(10, 64, 0);

        coordinator.start(location(0, 64, 0), target, List.of(gate), 0L);
        coordinator.gateOpenIntent(gate.key());
        assertEquals(List.of(gate.approach()), navigation.targets);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.approach(), 1L));
        assertEquals(List.of(gate.approach(), gate.exit()), navigation.targets);
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.exit(), 2L));
        assertEquals(List.of(gate.approach(), gate.exit()), navigation.targets);
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.exit(), 3L));
        assertEquals(List.of(gate.approach(), gate.exit(), target), navigation.targets);
        assertEquals(GateRouteCoordinator.Result.COMPLETE, coordinator.tick(target, 4L));
        assertEquals(1, navigation.cancelCount);
        assertFalse(coordinator.active());
    }

    @Test
    void xacNhanExitLapTrongCungServerTickKhongDuocTinhHaiLan() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);
        Location target = location(10, 64, 0);

        coordinator.start(location(0, 64, 0), target, List.of(gate), 0L);
        coordinator.tick(gate.approach(), 1L);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.exit(), 2L));
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.exit(), 2L));
        assertEquals(List.of(gate.approach(), gate.exit()), navigation.targets);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS, coordinator.tick(gate.exit(), 3L));
        assertEquals(List.of(gate.approach(), gate.exit(), target), navigation.targets);
    }

    @Test
    void twoSequentialConfiguredGatesRebuildNavigatorPathAfterFirstGate() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        GateRoute.Candidate firstGate = candidate(4, 6);
        GateRoute.Candidate secondGate = candidate(10, 12);
        Location finalTarget = location(18, 64, 0);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.start(location(0, 64, 0), finalTarget,
                        List.of(firstGate, secondGate), 0L));
        coordinator.gateOpenIntent(firstGate.key());
        assertEquals(List.of(firstGate.approach()), navigation.targets);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(firstGate.approach(), 1L));
        assertEquals(List.of(firstGate.approach(), firstGate.exit()), navigation.targets);
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(firstGate.exit(), 2L));
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(firstGate.exit(), 3L));
        assertEquals(List.of(firstGate.approach(), firstGate.exit(), secondGate.approach()),
                navigation.targets);

        // Gate 2 can receive open intent only after route rebuild for Gate 1.
        coordinator.gateOpenIntent(secondGate.key());
        assertTrue(coordinator.active());
        coordinator.cancel();
        assertFalse(coordinator.active());
    }

    @Test
    void cancelXoaRouteVaHuyNavigationTam() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        coordinator.start(location(0, 64, 0), location(10, 64, 0), List.of(candidate(4, 6)), 0L);

        coordinator.cancel();

        assertFalse(coordinator.active());
        assertEquals(1, navigation.cancelCount);
        assertEquals(GateRouteCoordinator.Result.IDLE, coordinator.tick(location(0, 64, 0), 1L));
    }

    @Test
    void saiDoCaoKhongDuocTinhLaDaToiLeg() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 0.5);
        GateRoute.Candidate gate = candidate(4, 6);

        coordinator.start(location(0, 64, 0), location(10, 64, 0), List.of(gate), 0L);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(4, 64.5, 0), 1L));
        assertEquals(List.of(gate.approach()), navigation.targets);
    }

    @Test
    void citizensCompleteSomChiKhoiDongLaiMotLanTrongCungLeg() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 3.0, 1.0);
        GateRoute.Candidate gate = new GateRoute.Candidate(
                "world:49:64:-17", location(48.5, 64, -17.5), location(49.5, 64, -15.5));

        coordinator.start(location(47.5, 64, -18.5), location(55.5, 64, -10.5), List.of(gate), 0L);
        coordinator.tick(gate.approach(), 1L);
        navigation.navigating = false;

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(48.4822, 64, -17.3), 2L));
        assertEquals(List.of(gate.approach(), gate.exit(), gate.exit()), navigation.targets);
        assertTrue(coordinator.active());

        navigation.navigating = false;
        assertEquals(GateRouteCoordinator.Result.FAILED,
                coordinator.tick(location(48.4822, 64, -17.3), 3L));
        assertEquals(List.of(gate.approach(), gate.exit(), gate.exit()), navigation.targets);
        assertFalse(coordinator.active());
    }

    @Test
    void restartThatBaiLoaiCandidateVaThuCandidateKeTiep() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        GateRoute.Candidate first = candidate(4, 6);
        GateRoute.Candidate second = candidate(8, 10);

        coordinator.start(location(0, 64, 0), location(20, 64, 0), List.of(first, second), 0L);
        navigation.navigating = false;
        navigation.failNextStart = true;

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(0, 64, 0), 1L));
        assertEquals(List.of(first.approach(), first.approach(), second.approach()), navigation.targets);
        assertTrue(coordinator.active());
    }

    @Test
    void navigationNhanEffectiveMarginTheoLeg() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 1.5, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);
        Location target = location(10, 64, 0);

        coordinator.start(location(0, 64, 0), target, List.of(gate), 0L);
        assertEquals(List.of(0.75), navigation.margins);

        coordinator.tick(gate.approach(), 1L);
        assertEquals(List.of(0.75, 0.75), navigation.margins);

        coordinator.tick(gate.exit(), 2L);
        coordinator.tick(gate.exit(), 3L);
        assertEquals(List.of(0.75, 0.75, 1.5), navigation.margins);
    }


    @Test
    void restartGiuDungEffectiveMarginCuaLeg() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 1.5, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);

        coordinator.start(location(0, 64, 0), location(10, 64, 0), List.of(gate), 0L);
        coordinator.tick(gate.approach(), 1L);
        assertEquals(List.of(0.75, 0.75), navigation.margins);

        navigation.navigating = false;
        coordinator.tick(gate.exit(), 2L);
        assertEquals(List.of(0.75, 0.75, 0.75), navigation.margins);
        assertEquals(gate.exit(), navigation.targets.get(2));
    }

    @Test
    void citizensCompleteOutsideEffectiveCrossingMarginKhoiDongLaiApproach() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        GateRoute.Candidate gate = new GateRoute.Candidate(
                "world:41:64:-12", location(41.5, 64, -12.5), location(41.5, 64, -10.5));

        coordinator.start(location(41.5, 64, -15.5), location(-8.0, 64, 8.0), List.of(gate), 0L);
        navigation.navigating = false;

        // Vị trí phải nằm ngoài margin quanh cả tọa độ tâm block lẫn block-goal của Citizens;
        // nếu chỉ ngoài tâm block thì đó là kết quả Citizens đã xác nhận hoàn tất, không phải
        // hoàn tất sớm, và khởi động lại leg sẽ lặp vô tận.
        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(41.4845, 64, -14.3854), 1L));
        assertEquals(List.of(gate.approach(), gate.approach()), navigation.targets);
    }

    @Test
    void citizensHoanTatTaiBlockGoalChuyenLegThayViLapLaiApproach() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.75, 1.0);
        // Tuyến GOING_TO_PLOT_GATE của Farmer Steve trong latest.log ngày 2026-08-16.
        GateRoute.Candidate gate = new GateRoute.Candidate(
                "StillCliff:61:-60:-1", location(60.5, -60, -0.5), location(62.5, -60, -0.5));
        Location target = location(69.5, -60, -9.5);

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.start(location(59.2656, -60, -0.8936), target, List.of(gate), 0L));
        // Block-goal không đủ để hoàn tất approach; coordinator phải giữ/retry approach thật.
        navigation.navigating = false;

        assertEquals(GateRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(location(59.2656, -60, -0.8936), 1L));
        assertEquals(List.of(gate.approach(), gate.approach()), navigation.targets);
        assertTrue(coordinator.active());
    }

    @Test
    void effectiveMarginGiuNguyenConfigKhiNhoHonCap() {
        FakeNavigation navigation = new FakeNavigation();
        GateRouteCoordinator coordinator = new GateRouteCoordinator(navigation, 20L, 0.5, 1.0);
        GateRoute.Candidate gate = candidate(4, 6);

        coordinator.start(location(0, 64, 0), location(10, 64, 0), List.of(gate), 0L);
        assertEquals(List.of(0.5), navigation.margins);

        coordinator.tick(gate.approach(), 1L);
        assertEquals(List.of(0.5, 0.5), navigation.margins);

        coordinator.tick(gate.exit(), 2L);
        coordinator.tick(gate.exit(), 3L);
        assertEquals(List.of(0.5, 0.5, 0.5), navigation.margins);
    }

    private GateRoute.Candidate candidate(int approachX, int exitX) {
        return new GateRoute.Candidate(
                "world:" + ((approachX + exitX) / 2) + ":64:0",
                location(approachX, 64, 0),
                location(exitX, 64, 0));
    }

    private Location location(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    private static final class FakeNavigation implements GateRouteCoordinator.Navigation {
        private final List<Location> targets = new ArrayList<>();
        private final List<Double> margins = new ArrayList<>();
        private final List<Long> generations = new ArrayList<>();
        private int cancelCount;
        private boolean navigating;
        private boolean failNextStart;
        private boolean recoverResult;
        private int recoverCount;
        private int requestCount;

        @Override
        public boolean start(Location target, double margin) {
            targets.add(target);
            margins.add(margin);
            if (failNextStart) {
                failNextStart = false;
                navigating = false;
                return false;
            }
            navigating = true;
            return true;
        }

        @Override
        public boolean start(Location target, double margin, GateRoute.Leg leg, long generation) {
            targets.add(target);
            margins.add(margin);
            generations.add(generation);
            if (failNextStart) {
                failNextStart = false;
                navigating = false;
                return false;
            }
            navigating = true;
            return true;
        }

        @Override
        public boolean navigating() {
            return navigating;
        }

        @Override
        public boolean recover(Location current, Location target, int radius) {
            recoverCount++;
            return recoverResult;
        }

        @Override
        public void cancel() {
            cancelCount++;
            navigating = false;
        }

        @Override
        public boolean requestGate(String gateKey) {
            requestCount++;
            return true;
        }
    }
}
