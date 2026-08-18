package vn.heomc.livingnpc;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Serializes door passages per normalized physical door, with bounded FIFO waiting. */
final class DoorPassageCoordinator {
    static final int DEFAULT_MAX_WAITERS = 8;

    enum Result { OWNER, WAITING, DUPLICATE, REJECTED }

    record DoorKey(String world, int x, int y, int z) {
        static DoorKey of(org.bukkit.block.Block block) {
            org.bukkit.block.Block bottom = DoubleDoorSupport.bottom(block);
            org.bukkit.block.Block partner = DoubleDoorSupport.findPartner(bottom);
            if (partner != null && compare(partner, bottom) < 0) bottom = partner;
            String world = bottom.getWorld() == null ? "unknown" : bottom.getWorld().getName();
            return new DoorKey(world, bottom.getX(), bottom.getY(), bottom.getZ());
        }
        private static int compare(org.bukkit.block.Block a, org.bukkit.block.Block b) {
            int x = Integer.compare(a.getX(), b.getX());
            return x != 0 ? x : Integer.compare(a.getZ(), b.getZ());
        }
    }

    private record Request(UUID npc, Runnable starter) {}
    private final int maxWaiters;
    private final BiConsumer<UUID, String> trace;
    private final Map<DoorKey, UUID> owners = new HashMap<>();
    private final Map<DoorKey, ArrayDeque<Request>> queues = new HashMap<>();
    private final Map<UUID, DoorKey> requestsByNpc = new HashMap<>();

    DoorPassageCoordinator() { this(DEFAULT_MAX_WAITERS, (ignoredNpc, ignoredResult) -> {}); }

    DoorPassageCoordinator(int maxWaiters, BiConsumer<UUID, String> trace) {
        if (maxWaiters < 1) throw new IllegalArgumentException("maxWaiters must be positive");
        this.maxWaiters = maxWaiters;
        this.trace = trace == null ? (ignoredNpc, ignoredResult) -> {} : trace;
    }

    synchronized Result request(DoorKey key, UUID npc, Runnable starter) {
        if (key == null || npc == null || starter == null) return Result.REJECTED;
        DoorKey existing = requestsByNpc.get(npc);
        if (existing != null) return Result.DUPLICATE;
        if (!owners.containsKey(key)) {
            owners.put(key, npc);
            requestsByNpc.put(npc, key);
            trace.accept(npc, "OWNER");
            return Result.OWNER;
        }
        ArrayDeque<Request> queue = queues.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        if (queue.size() >= maxWaiters) {
            trace.accept(npc, "REJECTED_QUEUE_FULL");
            return Result.REJECTED;
        }
        queue.addLast(new Request(npc, starter));
        requestsByNpc.put(npc, key);
        trace.accept(npc, "WAITING");
        return Result.WAITING;
    }

    synchronized void release(DoorKey key, UUID npc, String result) {
        if (key == null || npc == null) return;
        if (!npc.equals(owners.get(key))) {
            requestsByNpc.remove(npc, key);
            removeQueued(key, npc);
            return;
        }
        owners.remove(key);
        requestsByNpc.remove(npc, key);
        trace.accept(npc, result == null ? "RELEASED" : result);
        ArrayDeque<Request> queue = queues.get(key);
        Request next = queue == null ? null : queue.pollFirst();
        if (queue != null && queue.isEmpty()) queues.remove(key);
        if (next == null) return;
        owners.put(key, next.npc());
        requestsByNpc.put(next.npc(), key);
        trace.accept(next.npc(), "OWNER");
        try {
            next.starter().run();
        } catch (RuntimeException failure) {
            // Starter owns its own teardown; coordinator must still not strand next requests.
            release(key, next.npc(), "ABORTED_START");
            throw failure;
        }
    }

    synchronized void cancel(UUID npc, String result) {
        DoorKey key = requestsByNpc.get(npc);
        if (key == null) return;
        if (npc.equals(owners.get(key))) release(key, npc, result);
        else {
            requestsByNpc.remove(npc);
            removeQueued(key, npc);
            trace.accept(npc, result == null ? "CANCELLED" : result);
        }
    }

    synchronized void shutdown() {
        owners.clear();
        queues.clear();
        requestsByNpc.clear();
    }

    synchronized int ownerCount() { return owners.size(); }
    synchronized int waitingCount(DoorKey key) { return queues.getOrDefault(key, new ArrayDeque<>()).size(); }
    synchronized boolean owns(DoorKey key, UUID npc) { return npc.equals(owners.get(key)); }

    synchronized boolean owns(UUID npc, DoorKey key) {
        return key != null && npc != null && npc.equals(owners.get(key));
    }
    synchronized int trackedCount() { return requestsByNpc.size(); }

    private void removeQueued(DoorKey key, UUID npc) {
        ArrayDeque<Request> queue = queues.get(key);
        if (queue == null) return;
        queue.removeIf(request -> request.npc().equals(npc));
        if (queue.isEmpty()) queues.remove(key);
    }
}
