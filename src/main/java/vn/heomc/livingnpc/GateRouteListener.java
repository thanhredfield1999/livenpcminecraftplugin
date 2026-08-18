package vn.heomc.livingnpc;

import net.citizensnpcs.api.event.NPCOpenGateEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class GateRouteListener implements Listener {
    private final LivingNpcPlugin plugin;
    private final DoorPassageCoordinator passageCoordinator;
    private final OwnedTaskRegistry monitorTasks = new OwnedTaskRegistry();
    private boolean accepting = true;

    GateRouteListener(LivingNpcPlugin plugin, DoorPassageCoordinator passageCoordinator) {
        this.plugin = plugin;
        this.passageCoordinator = passageCoordinator;
    }

    void shutdown() {
        accepting = false;
        monitorTasks.cancelAll();
    }

    void resume() {
        accepting = true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcOpenGate(NPCOpenGateEvent event) {
        if (!accepting) {
            event.setCancelled(true);
            return;
        }
        if (plugin.manager() == null) return;
        FarmerDefinition definition = plugin.manager().definition(event.getNPC().getUniqueId());
        if (definition == null) return;
        VillageDefinition village = definition.villageId() == null ? null : plugin.villages().get(definition.villageId());
        if (village == null) return;
        String key = GateRouteDiscovery.gateKey(event.getGate());
        boolean configured = false;
        for (NavigationGate gate : village.navigationGates()) {
            if (GateRouteDiscovery.gateKey(event.getGate()).equals(
                    gate.location().world() + ":" + (int) Math.floor(gate.location().x()) + ":"
                            + (int) Math.floor(gate.location().y()) + ":" + (int) Math.floor(gate.location().z()))) {
                configured = true;
                if (!gate.allows(definition.activeRole())) event.setCancelled(true);
                break;
            }
        }
        if (!configured) event.setCancelled(true);
        if (event.isCancelled()) return;
        NPC npc = event.getNPC();
        DoorPassageCoordinator.DoorKey gateKey = DoorPassageCoordinator.DoorKey.of(event.getGate());
        DoorPassageCoordinator.Result result = passageCoordinator.request(
                gateKey, npc.getUniqueId(), () -> openQueuedGate(npc, event.getGate(), gateKey));
        if (result == DoorPassageCoordinator.Result.WAITING) {
            event.setCancelled(true);
            return;
        }
        if (result != DoorPassageCoordinator.Result.OWNER) {
            event.setCancelled(true);
            return;
        }
        plugin.manager().observeGateOpened(event.getNPC().getUniqueId(), key);
        if (plugin.ranchers() != null) plugin.ranchers().observeGateOpened(event.getNPC().getUniqueId(), key);
        monitorGate(npc, event.getGate(), gateKey);
    }

    private void openQueuedGate(NPC npc, Block gate, DoorPassageCoordinator.DoorKey key) {
        if (!LivingDoorExaminer.openAfterAuthorization(npc, gate, gate.getType())) {
            passageCoordinator.release(key, npc.getUniqueId(), "ABORTED_GATE_OPEN");
            return;
        }
        LivingDoorExaminer.scheduleManagedClose(npc, gate, gate.getType());
        monitorGate(npc, gate, key);
    }

    private void monitorGate(NPC npc, Block gate, DoorPassageCoordinator.DoorKey key) {
        final org.bukkit.scheduler.BukkitTask[] taskHolder = new org.bukkit.scheduler.BukkitTask[1];
        org.bukkit.scheduler.BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            private int elapsed;
            @Override public void run() {
                elapsed++;
                if (!(gate.getBlockData() instanceof Openable openable)) {
                    cancel();
                    monitorTasks.remove(taskHolder[0]);
                    passageCoordinator.release(key, npc.getUniqueId(), "RELEASED_GATE_INVALID");
                    return;
                }
                if (!openable.isOpen() || elapsed >= 240) {
                    cancel();
                    monitorTasks.remove(taskHolder[0]);
                    passageCoordinator.release(key, npc.getUniqueId(),
                            elapsed >= 240 ? "ABORTED_GATE_TIMEOUT" : "RESUMED_GATE");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        taskHolder[0] = task;
        monitorTasks.add(task);
    }
}
