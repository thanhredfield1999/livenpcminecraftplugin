package vn.heomc.livingnpc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
    static StoredLocation from(Location location) {
        return new StoredLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    static StoredLocation load(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String world = section.getString("world");
        if (world == null) {
            return null;
        }
        return new StoredLocation(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    Location resolve() {
        World resolved = Bukkit.getWorld(world);
        return resolved == null ? null : new Location(resolved, x, y, z, yaw, pitch);
    }

    void save(ConfigurationSection section) {
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
    }
}
