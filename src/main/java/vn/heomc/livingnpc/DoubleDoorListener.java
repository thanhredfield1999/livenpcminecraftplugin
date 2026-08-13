package vn.heomc.livingnpc;

import java.util.HashSet;
import java.util.Set;
import net.citizensnpcs.api.event.NPCOpenDoorEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

final class DoubleDoorListener implements Listener {
    private static final double CLOSE_DISTANCE_SQUARED = 1.8 * 1.8;
    private static final double OPEN_DISTANCE_SQUARED = 1.25 * 1.25;

    private final LivingNpcPlugin plugin;
    private final Set<BlockKey> synchronizing = new HashSet<>();

    DoubleDoorListener(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcOpenDoor(NPCOpenDoorEvent event) {
        if (plugin.manager() == null || !plugin.manager().manages(event.getNPC().getUniqueId())) return;
        Block source = DoubleDoorSupport.bottom(event.getDoor());
        if (!withinOpeningRange(event.getNPC().getStoredLocation(), source.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (synchronizing.contains(BlockKey.of(source))) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> openPartner(event.getNPC(), source));
    }

    static boolean withinOpeningRange(Location npcLocation, Location doorLocation) {
        if (npcLocation == null || doorLocation == null
                || npcLocation.getWorld() == null || !npcLocation.getWorld().equals(doorLocation.getWorld())) {
            return false;
        }
        return npcLocation.distanceSquared(doorLocation.clone().add(0.5, 0, 0.5)) <= OPEN_DISTANCE_SQUARED;
    }

    private void openPartner(NPC npc, Block source) {
        Block partner = DoubleDoorSupport.findPartner(source);
        if (partner == null || !(partner.getBlockData() instanceof Door door) || door.isOpen()) return;

        BlockKey key = BlockKey.of(partner);
        synchronizing.add(key);
        NPCOpenDoorEvent partnerEvent = new NPCOpenDoorEvent(npc, partner);
        try {
            Bukkit.getPluginManager().callEvent(partnerEvent);
        } finally {
            synchronizing.remove(key);
        }
        if (partnerEvent.isCancelled()) return;

        Material material = partner.getType();
        String closedState = partner.getBlockData().getAsString();
        Door opened = (Door) partner.getBlockData();
        opened.setOpen(true);
        partner.setBlockData(opened);
        closeAfterPassage(npc, partner, material, closedState);
    }

    private void closeAfterPassage(NPC npc, Block block, Material material, String closedState) {
        Location centre = block.getLocation().add(0.5, 0, 0.5);
        new BukkitRunnable() {
            private boolean approached;
            private int elapsedTicks;

            @Override
            public void run() {
                elapsedTicks++;
                BlockData current = block.getBlockData();
                if (block.getType() != material || !(current instanceof Door door) || !door.isOpen()) {
                    cancel();
                    return;
                }
                Location npcLocation = npc.getStoredLocation();
                boolean navigationEnded = !npc.getNavigator().isNavigating();
                boolean movedAway = npcLocation == null
                        || !npcLocation.getWorld().equals(centre.getWorld())
                        || npcLocation.distanceSquared(centre) > CLOSE_DISTANCE_SQUARED;
                if (!movedAway) approached = true;
                if (!navigationEnded && !(approached && movedAway) && elapsedTicks < 200) return;

                block.setBlockData(Bukkit.createBlockData(closedState));
                cancel();
            }
        }.runTaskTimer(plugin, 3L, 1L);
    }

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }
}
