package vn.heomc.livingnpc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

final class LinkedBlockListener implements Listener {
    private final LivingNpcPlugin plugin;

    LinkedBlockListener(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        for (VillageDefinition village : plugin.villages().villages()) {
            for (SeatDefinition seat : village.seats()) {
                if (!isLinkedToSeat(seat, broken)) continue;
                if (plugin.manager().removeSeat(village.id(), seat.id())) {
                    event.getPlayer().sendMessage(Component.text(
                            "[LivingNPC] Đã xóa liên kết "
                                    + (seat.type() == SeatType.DINING ? "ghế bàn ăn" : "ghế nghỉ")
                                    + " vì block liên quan đã bị phá.",
                            NamedTextColor.YELLOW));
                } else {
                    event.getPlayer().sendMessage(Component.text(
                            "[LivingNPC] Không thể lưu việc xóa liên kết ghế; hãy kiểm tra villages.yml.",
                            NamedTextColor.RED));
                }
                return;
            }
        }
    }

    private boolean isLinkedToSeat(SeatDefinition seat, Block broken) {
        StoredLocation stored = seat.location();
        if (!stored.world().equals(broken.getWorld().getName())) return false;
        Block seatBlock = broken.getWorld().getBlockAt(
                (int) Math.floor(stored.x()), (int) Math.floor(stored.y()), (int) Math.floor(stored.z()));
        if (seatBlock.equals(broken)) return true;
        if (seat.type() != SeatType.DINING || !(seatBlock.getBlockData() instanceof Stairs stairs)) return false;
        BlockFace front = SeatValidator.sittingFacing(stairs);
        return seatBlock.getRelative(front).equals(broken);
    }
}
