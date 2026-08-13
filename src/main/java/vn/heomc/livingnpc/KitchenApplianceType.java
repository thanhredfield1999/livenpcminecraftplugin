package vn.heomc.livingnpc;

import java.util.Locale;
import org.bukkit.Material;

enum KitchenApplianceType {
    FURNACE(Material.FURNACE),
    SMOKER(Material.SMOKER);

    private final Material material;

    KitchenApplianceType(Material material) {
        this.material = material;
    }

    Material material() {
        return material;
    }

    static KitchenApplianceType parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
