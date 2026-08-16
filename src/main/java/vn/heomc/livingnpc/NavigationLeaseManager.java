package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class NavigationLeaseManager {
    record Lease(String owner, int priority, Runnable onPreempt) {
        Lease {
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Lease owner is required");
            onPreempt = onPreempt == null ? () -> { } : onPreempt;
        }
    }

    private final Map<UUID, Lease> leases = new HashMap<>();

    boolean claim(UUID npcUuid, String owner, int priority, Runnable onPreempt) {
        Lease current = leases.get(npcUuid);
        if (current != null && current.owner().equals(owner)) return true;
        if (current != null && current.priority() >= priority) return false;
        leases.put(npcUuid, new Lease(owner, priority, onPreempt));
        if (current != null) {
            try {
                current.onPreempt().run();
            } catch (RuntimeException ignored) {
                // Owner mới đã thắng arbitration; callback cũ không được rollback lease.
            }
        }
        return true;
    }

    boolean heldBy(UUID npcUuid, String owner) {
        Lease lease = leases.get(npcUuid);
        return lease != null && lease.owner().equals(owner);
    }

    void release(UUID npcUuid, String owner) {
        Lease lease = leases.get(npcUuid);
        if (lease != null && lease.owner().equals(owner)) leases.remove(npcUuid);
    }

    void clear() {
        leases.clear();
    }
}
