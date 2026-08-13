package vn.heomc.livingnpc;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ProductionRecipeRegistry {
    private final File file;
    private final Logger logger;
    private Map<ResidentRole, List<ProductionRecipe>> recipes = Map.of();

    ProductionRecipeRegistry(File dataFolder, Logger logger) {
        file = new File(dataFolder, "recipes.yml");
        this.logger = logger;
        reload();
    }

    void reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("recipes");
        EnumMap<ResidentRole, List<ProductionRecipe>> loaded = new EnumMap<>(ResidentRole.class);
        Set<String> ids = new HashSet<>();
        if (root != null) for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            ResidentRole role = ResidentRole.parse(section == null ? null : section.getString("role"));
            String output = normalize(section == null ? null : section.getString("output"));
            int amount = section == null ? 0 : section.getInt("amount", 1);
            int target = section == null ? 0 : section.getInt("stock-target", 0);
            Map<String, Integer> inputs = loadInputs(section == null ? null : section.getConfigurationSection("inputs"));
            KitchenApplianceType appliance = KitchenApplianceType.parse(
                    section == null ? null : section.getString("appliance"));
            Map<String, Integer> fuel = loadInputs(section == null ? null : section.getConfigurationSection("fuel"));
            long cookTime = section == null ? 0L : section.getLong("cook-time-ticks");
            int servings = section == null ? 0 : section.getInt("servings");
            int nutrition = section == null ? 0 : section.getInt("nutrition");
            int hydration = section == null ? 0 : section.getInt("hydration");
            int priority = section == null ? 0 : section.getInt("priority", 50);
            String tool = normalize(section == null ? null : section.getString("tool"));
            boolean cookRecipe = role == ResidentRole.COOK;
            if (!ids.add(id) || role != ResidentRole.COOK && role != ResidentRole.CRAFTER
                    || output == null || amount <= 0 || target <= 0 || inputs.isEmpty() || inputs.containsKey(output)
                    || !validMaterials(inputs) || Material.matchMaterial(output) == null
                    || cookRecipe && (appliance == null || fuel.isEmpty() || !validMaterials(fuel)
                    || cookTime < 20L || servings <= 0 || nutrition < 0 || nutrition > 100
                    || hydration < 0 || hydration > 100 || priority < 0 || priority > 100)) {
                logger.warning("Bo qua recipe LivingNPC khong hop le: " + id);
                continue;
            }
            loaded.computeIfAbsent(role, ignored -> new ArrayList<>()).add(new ProductionRecipe(
                    id, role, inputs, output, amount, target,
                    section.getString("action", cookRecipe ? "Nau an" : "Che tao"),
                    appliance, fuel, cookTime, servings, nutrition, hydration, priority, tool));
        }
        if (hasCycle(loaded)) {
            logger.severe("recipes.yml co vong lap san xuat; Cook/Crafter da fail-closed.");
            recipes = Map.of();
            return;
        }
        EnumMap<ResidentRole, List<ProductionRecipe>> immutable = new EnumMap<>(ResidentRole.class);
        loaded.forEach((role, values) -> immutable.put(role, List.copyOf(values)));
        recipes = Map.copyOf(immutable);
    }

    List<ProductionRecipe> recipes(ResidentRole role) {
        return recipes.getOrDefault(role, List.of());
    }

    private Map<String, Integer> loadInputs(ConfigurationSection section) {
        Map<String, Integer> inputs = new HashMap<>();
        if (section == null) return inputs;
        for (String key : section.getKeys(false)) {
            String normalized = normalize(key);
            int amount = section.getInt(key);
            if (normalized != null && amount > 0) inputs.put(normalized, amount);
        }
        return inputs;
    }

    private boolean hasCycle(Map<ResidentRole, List<ProductionRecipe>> loaded) {
        Map<String, Set<String>> graph = new HashMap<>();
        loaded.values().stream().flatMap(List::stream).forEach(recipe ->
                graph.computeIfAbsent(recipe.output(), ignored -> new HashSet<>()).addAll(recipe.inputs().keySet()));
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        return graph.keySet().stream().anyMatch(node -> cyclic(node, graph, visiting, visited));
    }

    private boolean validMaterials(Map<String, Integer> items) {
        return items.keySet().stream().allMatch(key -> Material.matchMaterial(key) != null);
    }

    private boolean cyclic(String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(node)) return false;
        if (!visiting.add(node)) return true;
        for (String input : graph.getOrDefault(node, Set.of())) {
            if (graph.containsKey(input) && cyclic(input, graph, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("[a-z0-9_:-]+") ? normalized : null;
    }
}
