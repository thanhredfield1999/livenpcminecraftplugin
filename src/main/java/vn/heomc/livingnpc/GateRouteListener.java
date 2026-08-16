package vn.heomc.livingnpc;

import net.citizensnpcs.api.event.NPCOpenGateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class GateRouteListener implements Listener {
    private final LivingNpcPlugin plugin;

    GateRouteListener(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcOpenGate(NPCOpenGateEvent event) {
        String gateKey = GateRouteDiscovery.gateKey(event.getGate());
        if (plugin.manager() != null) {
            plugin.manager().observeGateOpened(event.getNPC().getUniqueId(), gateKey);
        }
        if (plugin.ranchers() != null) {
            plugin.ranchers().observeGateOpened(event.getNPC().getUniqueId(), gateKey);
        }
    }
}