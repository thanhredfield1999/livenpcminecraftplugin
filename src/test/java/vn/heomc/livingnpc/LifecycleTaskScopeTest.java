package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.heomc.livingnpc.api.lifecycle.DispatchResult;
import vn.heomc.livingnpc.api.lifecycle.LifecycleTicket;

final class LifecycleTaskScopeTest {
    @Test
    void ticketMoiLamTicketCuCuaCungOwnerBiStale() {
        LifecycleTaskScope scope = new LifecycleTaskScope(Runnable::run);

        LifecycleTicket first = scope.open("restaurant:orders").orElseThrow();
        LifecycleTicket second = scope.open("restaurant:orders").orElseThrow();

        assertFalse(scope.isCurrent(first));
        assertTrue(scope.isCurrent(second));
        assertTrue(second.generation() > first.generation());
    }

    @Test
    void invalidateLamTicketHienTaiBiStale() {
        LifecycleTaskScope scope = new LifecycleTaskScope(Runnable::run);
        LifecycleTicket ticket = scope.open("restaurant:onboarding").orElseThrow();

        scope.invalidate("restaurant:onboarding");

        assertFalse(scope.isCurrent(ticket));
        assertEquals(DispatchResult.STALE, scope.dispatch(ticket, () -> {}));
    }

    @Test
    void taskDaXepHangKiemTraLaiTicketTruocKhiChay() {
        List<Runnable> queued = new ArrayList<>();
        LifecycleTaskScope scope = new LifecycleTaskScope(queued::add);
        LifecycleTicket ticket = scope.open("restaurant:menu").orElseThrow();
        AtomicBoolean ran = new AtomicBoolean();

        assertEquals(DispatchResult.SCHEDULED, scope.dispatch(ticket, () -> ran.set(true)));
        scope.invalidate("restaurant:menu");
        queued.getFirst().run();

        assertFalse(ran.get());
    }

    @Test
    void closeTuChoiCongViecMoiVaVoHieuTaskDaXepHang() {
        List<Runnable> queued = new ArrayList<>();
        LifecycleTaskScope scope = new LifecycleTaskScope(queued::add);
        LifecycleTicket ticket = scope.open("restaurant:supply").orElseThrow();
        AtomicBoolean ran = new AtomicBoolean();
        assertEquals(DispatchResult.SCHEDULED, scope.dispatch(ticket, () -> ran.set(true)));

        scope.close();
        queued.getFirst().run();

        assertFalse(ran.get());
        assertTrue(scope.open("restaurant:supply").isEmpty());
        assertEquals(DispatchResult.SHUTTING_DOWN, scope.dispatch(ticket, () -> {}));
    }

    @Test
    void schedulerTuChoiDuocChuyenThanhKetQuaOnDinh() {
        LifecycleTaskScope scope = new LifecycleTaskScope(ignored -> {
            throw new IllegalStateException("scheduler disabled");
        });
        LifecycleTicket ticket = scope.open("restaurant:route").orElseThrow();

        assertEquals(DispatchResult.SCHEDULER_REJECTED, scope.dispatch(ticket, () -> {}));
        assertTrue(scope.isCurrent(ticket));
    }

    @Test
    void exceptionTuCallbackKhongBiNhanNhầmThanhSchedulerRejected() {
        LifecycleTaskScope scope = new LifecycleTaskScope(Runnable::run);
        LifecycleTicket ticket = scope.open("restaurant:callback").orElseThrow();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> scope.dispatch(ticket, () -> {
                    throw new IllegalStateException("callback failed");
                }));

        assertEquals("callback failed", failure.getMessage());
    }

    @Test
    void closeKhongHoanTatTrongKhiCallbackDaDuocChapNhanDangChay() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        LifecycleTaskScope scope = new LifecycleTaskScope(queued::add);
        LifecycleTicket ticket = scope.open("restaurant:handoff").orElseThrow();
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        AtomicReference<Throwable> taskFailure = new AtomicReference<>();
        assertEquals(DispatchResult.SCHEDULED, scope.dispatch(ticket, () -> {
            actionStarted.countDown();
            try {
                assertTrue(releaseAction.await(2, TimeUnit.SECONDS));
            } catch (Throwable failure) {
                taskFailure.set(failure);
            }
        }));
        Thread callback = Thread.ofPlatform().start(queued.getFirst());
        assertTrue(actionStarted.await(2, TimeUnit.SECONDS));
        AtomicBoolean closeCompleted = new AtomicBoolean();
        Thread close = Thread.ofPlatform().start(() -> {
            scope.close();
            closeCompleted.set(true);
        });

        Thread.sleep(50L);
        assertFalse(closeCompleted.get());
        releaseAction.countDown();
        callback.join(2_000L);
        close.join(2_000L);

        assertEquals(null, taskFailure.get());
        assertTrue(closeCompleted.get());
    }

    @Test
    void generationToanScopeKhongTaiSuDungSauInvalidate() {
        LifecycleTaskScope scope = new LifecycleTaskScope(Runnable::run);
        LifecycleTicket first = scope.open("owner-a").orElseThrow();
        scope.invalidate("owner-a");
        LifecycleTicket other = scope.open("owner-b").orElseThrow();
        LifecycleTicket reopened = scope.open("owner-a").orElseThrow();

        assertTrue(other.generation() > first.generation());
        assertTrue(reopened.generation() > other.generation());
        assertFalse(scope.isCurrent(first));
    }

    @Test
    void contractTuChoiInputNullVaOwnerRong() {
        LifecycleTaskScope scope = new LifecycleTaskScope(Runnable::run);
        LifecycleTicket ticket = scope.open("owner").orElseThrow();

        assertThrows(NullPointerException.class, () -> new LifecycleTaskScope(null));
        assertThrows(NullPointerException.class, () -> scope.open(null));
        assertThrows(IllegalArgumentException.class, () -> scope.open("  "));
        assertThrows(NullPointerException.class, () -> scope.invalidate(null));
        assertThrows(NullPointerException.class, () -> scope.isCurrent(null));
        assertThrows(NullPointerException.class, () -> scope.dispatch(null, () -> {}));
        assertThrows(NullPointerException.class, () -> scope.dispatch(ticket, null));
    }
}
