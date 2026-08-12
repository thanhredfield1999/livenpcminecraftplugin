package vn.heomc.livingnpc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;

final class CropScanner {
    private static final Set<Material> ALLOWED_CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    private CropScanner() {
    }

    static Deque<CropWork> scan(Location center, int radius) {
        Deque<CropWork> work = new ArrayDeque<>();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = centerY - 2; y <= centerY + 2; y++) {
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    if (ALLOWED_CROPS.contains(block.getType())
                            && block.getBlockData() instanceof Ageable ageable
                            && ageable.getAge() == ageable.getMaximumAge()) {
                        work.add(new CropWork(block.getLocation(), CropWork.Type.HARVEST, block.getType()));
                        break;
                    }
                    if (block.getType() == Material.FARMLAND && block.getRelative(0, 1, 0).isEmpty()) {
                        Block cropBlock = block.getRelative(0, 1, 0);
                        work.add(new CropWork(cropBlock.getLocation(), CropWork.Type.PLANT, inferCrop(cropBlock, center, radius)));
                        break;
                    }
                }
            }
        }
        return work;
    }

    static boolean isAllowedCrop(Material material) {
        return ALLOWED_CROPS.contains(material);
    }

    static Material inferCrop(Block emptyCropBlock, Location center, int plotRadius) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        int radius = Math.min(3, plotRadius);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block sample = emptyCropBlock.getRelative(x, 0, z);
                if (Math.abs(sample.getX() - center.getBlockX()) > plotRadius
                        || Math.abs(sample.getZ() - center.getBlockZ()) > plotRadius) {
                    continue;
                }
                Material material = sample.getType();
                if (ALLOWED_CROPS.contains(material)) {
                    counts.merge(material, 1, Integer::sum);
                }
            }
        }
        return dominantCrop(counts);
    }

    static Material dominantCrop(Map<Material, Integer> counts) {
        return counts.entrySet().stream()
                .filter(entry -> ALLOWED_CROPS.contains(entry.getKey()) && entry.getValue() > 0)
                .max(Map.Entry.<Material, Integer>comparingByValue()
                        .thenComparing(entry -> entry.getKey().name()))
                .map(Map.Entry::getKey)
                .orElse(Material.WHEAT);
    }
}
