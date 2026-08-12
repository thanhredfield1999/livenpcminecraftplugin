package vn.heomc.livingnpc;

import java.util.EnumSet;
import org.bukkit.Location;
import org.bukkit.Material;

final class WorkZoneValidator {
    private WorkZoneValidator() {
    }

    static WorkZoneValidation validate(Location center, VillageWorkZoneType type, int radius, int verticalRange) {
        EnumSet<Material> found = EnumSet.noneOf(Material.class);
        for (int x = -radius; x <= radius && !satisfies(type, found); x++) {
            for (int z = -radius; z <= radius && !satisfies(type, found); z++) {
                for (int y = -verticalRange; y <= verticalRange; y++) {
                    Material material = center.getWorld().getBlockAt(
                            center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z).getType();
                    if (material == Material.CHIPPED_ANVIL || material == Material.DAMAGED_ANVIL) {
                        material = Material.ANVIL;
                    }
                    if (isFenceOrGate(material)) {
                        material = Material.OAK_FENCE;
                    }
                    if (type.required().contains(material)) {
                        found.add(material);
                    }
                    if (satisfies(type, found)) break;
                }
            }
        }
        EnumSet<Material> missing = EnumSet.copyOf(type.required());
        missing.removeAll(found);
        return new WorkZoneValidation(found, missing);
    }

    static WorkZoneValidation evaluate(VillageWorkZoneType type, java.util.Set<Material> found) {
        EnumSet<Material> normalized = EnumSet.noneOf(Material.class);
        normalized.addAll(found);
        if (normalized.contains(Material.CHIPPED_ANVIL) || normalized.contains(Material.DAMAGED_ANVIL)) {
            normalized.add(Material.ANVIL);
        }
        if (normalized.stream().anyMatch(WorkZoneValidator::isFenceOrGate)) {
            normalized.add(Material.OAK_FENCE);
        }
        EnumSet<Material> missing = EnumSet.copyOf(type.required());
        missing.removeAll(normalized);
        return new WorkZoneValidation(normalized, missing);
    }

    private static boolean satisfies(VillageWorkZoneType type, java.util.Set<Material> found) {
        return found.containsAll(type.required());
    }

    private static boolean isFenceOrGate(Material material) {
        return material.name().endsWith("_FENCE") || material.name().endsWith("_FENCE_GATE");
    }
}
