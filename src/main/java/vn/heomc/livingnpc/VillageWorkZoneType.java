package vn.heomc.livingnpc;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

enum VillageWorkZoneType {
    WOOD(Set.of(Material.STONECUTTER, Material.CRAFTING_TABLE)),
    COOKING(Set.of(Material.FURNACE, Material.CRAFTING_TABLE)),
    CRAFTING(Set.of(Material.CRAFTING_TABLE, Material.SMITHING_TABLE, Material.ANVIL)),
    MINING(Set.of(Material.STONECUTTER, Material.BLAST_FURNACE)),
    SECURITY(Set.of(Material.BELL, Material.TARGET)),
    RANCH(Set.of(Material.HAY_BLOCK, Material.OAK_FENCE)),
    FISHING(Set.of(Material.WATER));

    private final Set<Material> required;

    VillageWorkZoneType(Set<Material> required) {
        this.required = required;
    }

    Set<Material> required() {
        return required;
    }

    String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    static VillageWorkZoneType parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
