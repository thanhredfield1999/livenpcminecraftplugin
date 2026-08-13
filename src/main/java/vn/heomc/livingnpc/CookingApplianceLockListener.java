package vn.heomc.livingnpc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

final class CookingApplianceLockListener implements Listener {
    private static final Component LOCKED = Component.text(
            "[LivingNPC] Lò đang thuộc một phiên nấu và chưa được đối soát.", NamedTextColor.RED);
    private final CookingSessionStore sessions;

    CookingApplianceLockListener(CookingSessionStore sessions) {
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onOpen(InventoryOpenEvent event) {
        if (!locked(event.getInventory())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(LOCKED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onClick(InventoryClickEvent event) {
        if (!locked(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        event.getWhoClicked().sendMessage(LOCKED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onDrag(InventoryDragEvent event) {
        if (!locked(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        event.getWhoClicked().sendMessage(LOCKED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onMove(InventoryMoveItemEvent event) {
        if (locked(event.getSource()) || locked(event.getDestination())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (!locked(event.getBlock())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(LOCKED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::locked)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::locked)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::locked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::locked);
    }

    private boolean locked(Inventory inventory) {
        return inventory.getHolder() instanceof BlockState state && locked(state.getBlock());
    }

    private boolean locked(Block block) {
        return sessions.locked(CookingApplianceKey.from(block));
    }
}
