package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductionRecipeRegistryTest {
    @TempDir
    Path directory;

    @Test
    void loadsValidatedRecipes() throws Exception {
        Files.writeString(directory.resolve("recipes.yml"), """
                recipes:
                  bread:
                    role: cook
                    appliance: furnace
                    inputs: {wheat: 3}
                    fuel: {coal: 1}
                    output: bread
                    amount: 1
                    cook-time-ticks: 160
                    servings: 1
                    nutrition: 35
                    hydration: 0
                    priority: 70
                    stock-target: 8
                    action: Lam banh
                """);

        ProductionRecipeRegistry registry = new ProductionRecipeRegistry(
                directory.toFile(), Logger.getAnonymousLogger());

        assertEquals(1, registry.recipes(ResidentRole.COOK).size());
        assertEquals(8, registry.recipes(ResidentRole.COOK).getFirst().stockTarget());
        assertEquals(KitchenApplianceType.FURNACE,
                registry.recipes(ResidentRole.COOK).getFirst().appliance());
    }

    @Test
    void rejectsCookRecipeWithoutSeasonEightSchema() throws Exception {
        Files.writeString(directory.resolve("recipes.yml"), """
                recipes:
                  legacy_food:
                    role: cook
                    inputs: {wheat: 1}
                    output: bread
                    amount: 1
                    stock-target: 8
                """);

        ProductionRecipeRegistry registry = new ProductionRecipeRegistry(
                directory.toFile(), Logger.getAnonymousLogger());

        assertTrue(registry.recipes(ResidentRole.COOK).isEmpty());
    }

    @Test
    void failsClosedWhenRecipeGraphContainsCycle() throws Exception {
        Files.writeString(directory.resolve("recipes.yml"), """
                recipes:
                  first:
                    role: crafter
                    inputs: {item_b: 1}
                    output: item_a
                    amount: 1
                    stock-target: 8
                  second:
                    role: crafter
                    inputs: {item_a: 1}
                    output: item_b
                    amount: 1
                    stock-target: 8
                """);

        ProductionRecipeRegistry registry = new ProductionRecipeRegistry(
                directory.toFile(), Logger.getAnonymousLogger());

        assertTrue(registry.recipes(ResidentRole.CRAFTER).isEmpty());
    }
}
