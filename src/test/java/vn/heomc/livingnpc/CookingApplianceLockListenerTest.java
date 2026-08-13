package vn.heomc.livingnpc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CookingApplianceLockListenerTest {
    @TempDir
    Path temporaryDirectory;
    private CookingApplianceLockListener listener;
    private Inventory lockedInventory;

    @BeforeEach
    void setUp() {
        CookingSessionStore store = new CookingSessionStore(
                temporaryDirectory.toFile(), Logger.getLogger("CookingApplianceLockListenerTest"));
        CookingApplianceKey key = new CookingApplianceKey("StillCliff", 10, 64, 20);
        store.create(new CookingSession(
                UUID.randomUUID(), "stillcliff_1", UUID.randomUUID(), "furnace_1", key, "cooked_cod",
                Map.of("cod", 1), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                0L, 0L, 200L, CookingPhase.RESERVED));
        listener = new CookingApplianceLockListener(store);
        lockedInventory = inventoryAt(key);
    }

    @Test
    void blocksPlayerOpeningLockedAppliance() {
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getInventory()).thenReturn(lockedInventory);
        when(event.getPlayer()).thenReturn(mock(HumanEntity.class));

        listener.onOpen(event);

        verify(event).setCancelled(true);
    }

    @Test
    void blocksHopperMovingIntoLockedAppliance() {
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        when(event.getSource()).thenReturn(mock(Inventory.class));
        when(event.getDestination()).thenReturn(lockedInventory);

        listener.onMove(event);

        verify(event).setCancelled(true);
    }

    private Inventory inventoryAt(CookingApplianceKey key) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(key.world());
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(key.x());
        when(block.getY()).thenReturn(key.y());
        when(block.getZ()).thenReturn(key.z());
        Furnace holder = mock(Furnace.class);
        when(holder.getBlock()).thenReturn(block);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        return inventory;
    }
}
