package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

final class RuntimeStopCoordinatorTest {
    @Test
    void stopChayTatCaCleanupDungMotLanVaTiepTucSauLoi() {
        List<String> calls = new ArrayList<>();
        RuntimeStopCoordinator coordinator = new RuntimeStopCoordinator(
                Logger.getLogger("runtime-stop-test"),
                List.of(
                        new RuntimeStopCoordinator.Cleanup("doors", () -> calls.add("doors")),
                        new RuntimeStopCoordinator.Cleanup("visitors", () -> {
                            calls.add("visitors");
                            throw new IllegalStateException("loi gia lap");
                        }),
                        new RuntimeStopCoordinator.Cleanup("residents", () -> calls.add("residents"))));

        coordinator.start();
        coordinator.stop();
        coordinator.stop();

        assertEquals(List.of("doors", "visitors", "residents"), calls);
    }

    @Test
    void startMoiChoPhepCleanupLaiSauKhiRuntimeDuocBatLai() {
        List<String> calls = new ArrayList<>();
        RuntimeStopCoordinator coordinator = new RuntimeStopCoordinator(
                Logger.getLogger("runtime-restart-test"),
                List.of(new RuntimeStopCoordinator.Cleanup("residents", () -> calls.add("residents"))));

        coordinator.start();
        coordinator.stop();
        coordinator.start();
        coordinator.stop();

        assertEquals(List.of("residents", "residents"), calls);
    }

    @Test
    void coordinatorMoiXemRuntimeDaHoatDongDeStartupDisabledVanCleanup() {
        List<String> calls = new ArrayList<>();
        RuntimeStopCoordinator coordinator = new RuntimeStopCoordinator(
                Logger.getLogger("runtime-startup-disabled-test"),
                List.of(new RuntimeStopCoordinator.Cleanup("residents", () -> calls.add("residents"))));

        coordinator.stop();

        assertEquals(List.of("residents"), calls);
    }
}
