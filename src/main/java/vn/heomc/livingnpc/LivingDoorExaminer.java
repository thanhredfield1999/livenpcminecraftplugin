package vn.heomc.livingnpc;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.BlockSource;
import net.citizensnpcs.api.astar.pathfinder.MinecraftBlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.PathPoint;
import net.citizensnpcs.api.event.NPCOpenDoorEvent;
import net.citizensnpcs.api.event.NPCOpenGateEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bản thay thế fail-closed cho Citizens DoorExaminer 2.0.42.
 *
 * <p>Upstream giữ callback cửa sau khi navigation bị replace và dereference/cast block
 * mà không kiểm tra lại. Examiner này giữ event contract của Citizens nhưng xác thực
 * block tại cả thời điểm mở lẫn đóng.</p>
 */
final class LivingDoorExaminer implements BlockExaminer {
    private static final double OPEN_DISTANCE_SQUARED = 2.5 * 2.5;
    private static final double CLOSE_DISTANCE_SQUARED = 1.8 * 1.8;
    private static final int CLOSE_TASK_TIMEOUT_TICKS = 240;
    private static final Set<CloseTask> CLOSE_TASKS = new HashSet<>();
    private static boolean accepting = true;

    @Override
    public float getCost(BlockSource source, PathPoint point) {
        return 0.0F;
    }

    @Override
    public PassableState isPassable(BlockSource source, PathPoint point) {
        if (source == null || point == null || point.getVector() == null) return PassableState.IGNORE;
        Material material = source.getMaterialAt(point.getVector());
        if (material == null) return PassableState.IGNORE;
        if (MinecraftBlockExaminer.isGate(material)) {
            point.addCallback(new DoorOpener());
            return PassableState.PASSABLE;
        }
        if (!MinecraftBlockExaminer.isDoor(material)) return PassableState.IGNORE;
        BlockData data = source.getBlockDataAt(point.getVector());
        if (!(data instanceof Bisected bisected) || bisected.getHalf() != Bisected.Half.BOTTOM) {
            return PassableState.IGNORE;
        }
        point.addCallback(new DoorOpener());
        return PassableState.PASSABLE;
    }

    /**
     * Chốt examiner rồi kết thúc mọi close-task còn lại.
     *
     * <p>Examiner nằm trong {@code NavigatorParameters} mặc định của NPC được quản lý nên
     * Citizens vẫn có thể gọi lại callback sau khi runtime đã dừng. Vì vậy stop phải là một
     * chuyển trạng thái: sau đây examiner từ chối mở block và tạo close-task mới cho tới khi
     * {@link #resume()} được gọi. Mỗi task được kết thúc độc lập để một lỗi không bỏ sót
     * các task còn lại.</p>
     */
    static void shutdown() {
        accepting = false;
        RuntimeException failure = null;
        for (CloseTask task : Set.copyOf(CLOSE_TASKS)) {
            try {
                task.finish(true);
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
        }
        CLOSE_TASKS.clear();
        if (failure != null) throw failure;
    }

    /** Mở lại examiner khi runtime được bật lại. Idempotent. */
    static void resume() {
        accepting = true;
    }

    static boolean closeIfStillOpen(Block block, Material expectedMaterial) {
        if (block == null || expectedMaterial == null || block.getType() != expectedMaterial) return false;
        BlockData data = block.getBlockData();
        if (!(data instanceof Openable openable) || !openable.isOpen()) return false;
        openable.setOpen(false);
        block.setBlockData(openable);
        return true;
    }

    static final class DoorOpener implements PathPoint.PathCallback {
        private final OpenAction openAction;
        private final CloseScheduler closeScheduler;
        private final Predicate<Material> supportedMaterial;
        private boolean attempted;

        DoorOpener() {
            this(LivingDoorExaminer::openIfStillValid,
                    LivingDoorExaminer::scheduleManagedClose,
                    LivingDoorExaminer::isSupported);
        }

        DoorOpener(
                OpenAction openAction, CloseScheduler closeScheduler,
                Predicate<Material> supportedMaterial) {
            this.openAction = Objects.requireNonNull(openAction, "openAction");
            this.closeScheduler = Objects.requireNonNull(closeScheduler, "closeScheduler");
            this.supportedMaterial = Objects.requireNonNull(supportedMaterial, "supportedMaterial");
        }

        @Override
        public void run(NPC npc, Block point, List<Block> path, int index) {
            if (!accepting || attempted || npc == null || point == null) return;
            Material material = point.getType();
            if (!supportedMaterial.test(material)
                    || !withinDistance(npc.getStoredLocation(), point.getLocation(), OPEN_DISTANCE_SQUARED)) {
                return;
            }
            attempted = true;
            if (openAction.open(npc, point, material)) {
                try {
                    closeScheduler.schedule(npc, point, material);
                } catch (RuntimeException schedulingFailure) {
                    closeIfStillOpen(point, material);
                }
            }
        }

        @Override
        public void onReached(NPC npc, Block point) {
            // Close-task đã được sở hữu ngay khi examiner thực sự mở block.
        }
    }

    @FunctionalInterface
    interface OpenAction {
        boolean open(NPC npc, Block block, Material expectedMaterial);
    }

    @FunctionalInterface
    interface CloseScheduler {
        void schedule(NPC npc, Block block, Material expectedMaterial);
    }

    private static boolean openIfStillValid(NPC npc, Block block, Material expectedMaterial) {
        if (!accepting || block == null || block.getType() != expectedMaterial
                || !isSupported(expectedMaterial)) return false;
        BlockData before = block.getBlockData();
        if (!(before instanceof Openable openable) || openable.isOpen()) return false;

        Cancellable cancellable = MinecraftBlockExaminer.isDoor(expectedMaterial)
                ? new NPCOpenDoorEvent(npc, block)
                : new NPCOpenGateEvent(npc, block);
        Bukkit.getPluginManager().callEvent((Event) cancellable);
        if (cancellable.isCancelled()) return false;
        return openAfterAuthorization(npc, block, expectedMaterial);
    }

    static boolean openAfterAuthorization(NPC npc, Block block, Material expectedMaterial) {
        if (!accepting || block == null || block.getType() != expectedMaterial
                || !isSupported(expectedMaterial)) return false;
        BlockData current = block.getBlockData();
        if (!(current instanceof Openable currentOpenable) || currentOpenable.isOpen()) return false;
        currentOpenable.setOpen(true);
        block.setBlockData(currentOpenable);
        if (npc != null && npc.getEntity() instanceof LivingEntity livingEntity) livingEntity.swingMainHand();
        return true;
    }

    static void scheduleManagedClose(NPC npc, Block block, Material expectedMaterial) {
        if (!accepting) return;
        new CloseTask(npc, block, expectedMaterial).schedule();
    }

    private static boolean isSupported(Material material) {
        return material != null
                && (MinecraftBlockExaminer.isDoor(material) || MinecraftBlockExaminer.isGate(material));
    }

    private static boolean withinDistance(Location current, Location block, double maximumSquared) {
        if (current == null || block == null || current.getWorld() == null || block.getWorld() == null
                || !current.getWorld().equals(block.getWorld())) return false;
        return current.distanceSquared(block.clone().add(0.5, 0.0, 0.5)) <= maximumSquared;
    }

    private static final class CloseTask extends BukkitRunnable {
        private final NPC npc;
        private final Block block;
        private final Material expectedMaterial;
        private final Location centre;
        private final Object openingStrategy;
        private final CloseState closeState = new CloseState();
        private BukkitTask task;
        private int elapsedTicks;

        private CloseTask(NPC npc, Block block, Material expectedMaterial) {
            this.npc = npc;
            this.block = block;
            this.expectedMaterial = expectedMaterial;
            this.centre = block.getLocation().add(0.5, 0.0, 0.5);
            Navigator navigator = npc.getNavigator();
            this.openingStrategy = navigator == null ? null : navigator.getPathStrategy();
        }

        private void schedule() {
            task = runTaskTimer(JavaPlugin.getProvidingPlugin(LivingDoorExaminer.class), 3L, 1L);
            CLOSE_TASKS.add(this);
        }

        @Override
        public void run() {
            elapsedTicks++;
            if (block.getType() != expectedMaterial || !(block.getBlockData() instanceof Openable)) {
                finish(false);
                return;
            }
            Location current = npc.getStoredLocation();
            boolean sameWorld = current != null && current.getWorld() != null
                    && current.getWorld().equals(centre.getWorld())
                    && centre.getWorld() != null;
            double distanceSquared = sameWorld
                    ? current.distanceSquared(centre) : Double.POSITIVE_INFINITY;
            Navigator navigator = npc.getNavigator();
            boolean sameNavigation = openingStrategy != null && navigator != null
                    && navigator.getPathStrategy() == openingStrategy;
            if (closeState.shouldClose(
                    npc.isSpawned(), navigator != null && navigator.isNavigating(), sameWorld, sameNavigation,
                    distanceSquared, elapsedTicks)) {
                finish(true);
            }
        }

        private void finish(boolean close) {
            BukkitTask currentTask = task;
            task = null;
            try {
                if (close) closeIfStillOpen(block, expectedMaterial);
            } finally {
                try {
                    if (currentTask != null) currentTask.cancel();
                } finally {
                    CLOSE_TASKS.remove(this);
                }
            }
        }
    }

    static final class CloseState {
        private boolean enteredCloseRadius;

        boolean shouldClose(
                boolean spawned, boolean navigating, boolean sameWorld, boolean sameNavigation,
                double distanceSquared, int elapsedTicks) {
            if (!spawned || !sameWorld || elapsedTicks >= CLOSE_TASK_TIMEOUT_TICKS) {
                return true;
            }
            if (distanceSquared <= CLOSE_DISTANCE_SQUARED) {
                enteredCloseRadius = true;
                return false;
            }
            if (!navigating || !sameNavigation) return true;
            return enteredCloseRadius && distanceSquared > CLOSE_DISTANCE_SQUARED;
        }
    }
}
