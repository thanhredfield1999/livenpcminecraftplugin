package vn.heomc.livingnpc;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldedit.math.BlockVector3;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;

final class WorldMutationPolicy {
    static final String MINING_REGION_PREFIX = "lnpc-mine-";
    private final boolean worldGuardAvailable;
    private final boolean requireWorldGuard;

    WorldMutationPolicy(PluginManager pluginManager, boolean requireWorldGuard) {
        this.worldGuardAvailable = pluginManager.isPluginEnabled("WorldGuard");
        this.requireWorldGuard = requireWorldGuard;
    }

    boolean allows(Location location, MutationType mutationType) {
        if (!worldGuardAvailable) {
            return !requireWorldGuard;
        }
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location adapted = BukkitAdapter.adapt(location);
            StateFlag.State action = query.queryState(
                    adapted, null, mutationType == MutationType.BREAK ? Flags.BLOCK_BREAK : Flags.BLOCK_PLACE);
            // Spawn protection can deny BUILD to players while this bounded worker still tends its assigned plot.
            return action != StateFlag.State.DENY;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    boolean available() {
        return worldGuardAvailable;
    }

    boolean hasMiningRegion(Location location) {
        return !miningRegionsAt(location).isEmpty();
    }

    boolean inSameMiningRegion(Location center, Location candidate) {
        if (center == null || candidate == null || center.getWorld() == null || candidate.getWorld() == null
                || !center.getWorld().equals(candidate.getWorld())) return false;
        return miningRegionsAt(center).stream().anyMatch(region ->
                region.contains(candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ()));
    }

    static boolean isMiningRegionId(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).startsWith(MINING_REGION_PREFIX);
    }

    private java.util.List<ProtectedRegion> miningRegionsAt(Location location) {
        if (!worldGuardAvailable || location == null || location.getWorld() == null) return java.util.List.of();
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) return java.util.List.of();
            BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            return manager.getApplicableRegions(point).getRegions().stream()
                    .filter(region -> region.isPhysicalArea() && isMiningRegionId(region.getId()))
                    .toList();
        } catch (RuntimeException | LinkageError exception) {
            return java.util.List.of();
        }
    }
}
