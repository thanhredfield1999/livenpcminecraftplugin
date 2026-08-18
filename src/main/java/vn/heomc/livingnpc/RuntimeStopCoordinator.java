package vn.heomc.livingnpc;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sở hữu một chuyển trạng thái runtime duy nhất theo cả hai chiều.
 *
 * <p>{@code stop} chạy dọn dẹp có giới hạn, {@code start} mở lại các thành phần đã bị chốt
 * khi runtime dừng. Cả hai chiều đều tiếp tục sau lỗi của từng bước để một thành phần hỏng
 * không bỏ sót các thành phần còn lại.</p>
 */
final class RuntimeStopCoordinator {
    record Cleanup(String name, Runnable action) {
        Cleanup {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(action, "action");
        }
    }

    private final Logger logger;
    private final List<Cleanup> resumes;
    private final List<Cleanup> cleanups;
    private boolean running = true;

    RuntimeStopCoordinator(Logger logger, List<Cleanup> cleanups) {
        this(logger, List.of(), cleanups);
    }

    /**
     * @param resumes bước mở lại, phải idempotent vì {@code start} có thể chạy nhiều lần
     * @param cleanups bước dọn dẹp theo thứ tự phụ thuộc
     */
    RuntimeStopCoordinator(Logger logger, List<Cleanup> resumes, List<Cleanup> cleanups) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.resumes = List.copyOf(resumes);
        this.cleanups = List.copyOf(cleanups);
    }

    void start() {
        running = true;
        run(resumes, "resume");
    }

    void stop() {
        if (!running) return;
        running = false;
        run(cleanups, "cleanup");
    }

    private void run(List<Cleanup> steps, String phase) {
        for (Cleanup step : steps) {
            try {
                step.action().run();
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE,
                        "LivingNPC runtime " + phase + " failed: " + step.name()
                                + "; remaining " + phase + " will continue",
                        exception);
            }
        }
    }
}
