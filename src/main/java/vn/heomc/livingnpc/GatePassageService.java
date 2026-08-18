package vn.heomc.livingnpc;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import net.citizensnpcs.api.npc.NPC;

/** Mở fence gate sau APPROACH, qua FIFO coordinator. */
final class GatePassageService {
    private static GatePassageService active;
    private final DoorPassageCoordinator coordinator;

    GatePassageService(DoorPassageCoordinator coordinator) {
        this.coordinator = java.util.Objects.requireNonNull(coordinator, "coordinator");
        active = this;
    }

    static boolean isActive() {
        return active != null;
    }

    void shutdown() {
        coordinator.shutdown();
        if (active == this) active = null;
    }

    void resume() {
        active = this;
    }

    static boolean request(NPC npc, String gateKey) {
        return active != null && active.requestGate(npc, gateKey);
    }

    static void release(NPC npc, String gateKey) {
        if (active == null || npc == null) return;
        Block gate = block(gateKey);
        if (gate != null) active.coordinator.release(
                DoorPassageCoordinator.DoorKey.of(gate), npc.getUniqueId(), "ROUTE_RELEASED");
    }

    private boolean requestGate(NPC npc, String gateKey) {
        Block gate = block(gateKey);
        if (npc == null || gate == null || !npc.isSpawned()) {
            Bukkit.getLogger().info("[LivingNPC] NPC_GATE_TRACE result=PRECONDITION npc="
                    + (npc == null ? "null" : npc.getUniqueId()) + " gate=" + gateKey
                    + " block=" + (gate == null ? "null" : gate.getType())
                    + " spawned=" + (npc != null && npc.isSpawned()));
            return false;
        }
        DoorPassageCoordinator.DoorKey key = DoorPassageCoordinator.DoorKey.of(gate);
        DoorPassageCoordinator.Result result = coordinator.request(key, npc.getUniqueId(),
                () -> open(npc, gate, key));
        Bukkit.getLogger().info("[LivingNPC] NPC_GATE_TRACE result=FIFO_" + result + " npc="
                + npc.getUniqueId() + " gate=" + gateKey + " type=" + gate.getType());
        if (result == DoorPassageCoordinator.Result.WAITING) return false;
        if (result != DoorPassageCoordinator.Result.OWNER) return false;
        return open(npc, gate, key);
    }

    private boolean open(NPC npc, Block gate, DoorPassageCoordinator.DoorKey key) {
        if (!(gate.getBlockData() instanceof org.bukkit.block.data.Openable openable)) {
            Bukkit.getLogger().info("[LivingNPC] NPC_GATE_TRACE result=NOT_OPENABLE npc="
                    + npc.getUniqueId() + " gate=" + key);
            coordinator.release(key, npc.getUniqueId(), "GATE_NOT_OPENABLE");
            return false;
        }
        if (openable.isOpen()) {
            Bukkit.getLogger().info("[LivingNPC] NPC_GATE_TRACE result=ALREADY_OPEN npc="
                    + npc.getUniqueId() + " gate=" + key);
            return true;
        }
        boolean opened = LivingDoorExaminer.openAfterAuthorization(npc, gate, gate.getType());
        Bukkit.getLogger().info("[LivingNPC] NPC_GATE_TRACE result=OPEN_" + opened + " npc="
                + npc.getUniqueId() + " gate=" + key);
        if (opened) LivingDoorExaminer.scheduleManagedClose(npc, gate, gate.getType());
        else coordinator.release(key, npc.getUniqueId(), "GATE_OPEN_FAILED");
        return opened;
    }

    private static Block block(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", -1);
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        try {
            return world == null ? null : world.getBlockAt(
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
