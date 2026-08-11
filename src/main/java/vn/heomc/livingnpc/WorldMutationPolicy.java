package vn.heomc.livingnpc;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;

final class WorldMutationPolicy {
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
            return switch (mutationType) {
                case BREAK -> query.testState(adapted, null, Flags.BUILD, Flags.BLOCK_BREAK);
                case PLACE -> query.testState(adapted, null, Flags.BUILD, Flags.BLOCK_PLACE);
            };
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    boolean available() {
        return worldGuardAvailable;
    }
}
