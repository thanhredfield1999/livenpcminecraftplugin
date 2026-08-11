package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class CropScannerTest {
    @Test
    void plantsTheDominantNearbyCrop() {
        assertEquals(Material.CARROTS, CropScanner.dominantCrop(Map.of(
                Material.WHEAT, 2,
                Material.CARROTS, 7,
                Material.POTATOES, 1)));
    }

    @Test
    void defaultsToWheatWhenThePlotHasNoCropPattern() {
        assertEquals(Material.WHEAT, CropScanner.dominantCrop(Map.of()));
    }
}
