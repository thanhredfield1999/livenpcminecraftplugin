package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import vn.heomc.livingnpc.api.lifecycle.DispatchResult;
import vn.heomc.livingnpc.api.lifecycle.LifecycleScope;
import vn.heomc.livingnpc.api.lifecycle.LifecycleTicket;

final class LifecycleTaskScope implements LifecycleScope {
    private final Consumer<Runnable> scheduler;
    private final Map<String, Long> ownerGenerations = new HashMap<>();
    private long nextGeneration;
    private boolean closed;

    LifecycleTaskScope(Consumer<Runnable> scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public synchronized Optional<LifecycleTicket> open(String ownerId) {
        requireOwner(ownerId);
        if (closed) return Optional.empty();
        long generation = nextGeneration = Math.incrementExact(nextGeneration);
        ownerGenerations.put(ownerId, generation);
        return Optional.of(new LifecycleTicket(ownerId, generation));
    }

    @Override
    public synchronized void invalidate(String ownerId) {
        requireOwner(ownerId);
        ownerGenerations.remove(ownerId);
    }

    @Override
    public synchronized boolean isCurrent(LifecycleTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return isCurrentLocked(ticket);
    }

    @Override
    public synchronized DispatchResult dispatch(LifecycleTicket ticket, Runnable action) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(action, "action");
        if (closed) return DispatchResult.SHUTTING_DOWN;
        if (!isCurrentLocked(ticket)) return DispatchResult.STALE;
        AtomicReference<RuntimeException> callbackFailure = new AtomicReference<>();
        try {
            scheduler.accept(() -> {
                synchronized (LifecycleTaskScope.this) {
                    if (!isCurrentLocked(ticket)) return;
                    try {
                        action.run();
                    } catch (RuntimeException failure) {
                        callbackFailure.set(failure);
                        throw failure;
                    }
                }
            });
            return DispatchResult.SCHEDULED;
        } catch (RuntimeException rejected) {
            if (callbackFailure.get() == rejected) throw rejected;
            return DispatchResult.SCHEDULER_REJECTED;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        ownerGenerations.clear();
    }

    private boolean isCurrentLocked(LifecycleTicket ticket) {
        return !closed
                && ownerGenerations.getOrDefault(ticket.ownerId(), 0L) == ticket.generation();
    }

    private static void requireOwner(String ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        if (ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
    }
}
