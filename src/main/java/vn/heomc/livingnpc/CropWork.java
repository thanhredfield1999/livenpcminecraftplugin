package vn.heomc.livingnpc;

import org.bukkit.Location;
import org.bukkit.Material;

record CropWork(Location location, Type type, Material crop) {
    enum Type {
        HARVEST,
        PLANT
    }
}
