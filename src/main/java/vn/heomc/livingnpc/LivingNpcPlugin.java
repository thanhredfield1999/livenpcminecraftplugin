package vn.heomc.livingnpc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Gate;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class LivingNpcPlugin extends JavaPlugin {
    private FarmerManager manager;
    private ProfileRegistry profiles;
    private ResidentGui residentGui;
    private NpcEconomy economy;
    private GeminiSettings geminiSettings;
    private WorldMutationPolicy mutationPolicy;
    private VillageStore villageStore;
    private LivingNpcConfig config;
    private VisitorManager visitorManager;
    private RancherManager rancherManager;
    private FisherManager fisherManager;
    private CivilProfessionManager civilProfessionManager;
    private MerchantManager merchantManager;
    private ProfessionMonitor professionMonitor;
    private NeedsManager needsManager;
    private ProductionRecipeRegistry recipes;
    private MiningRestorationStore miningRestorations;
    private CookingSessionStore cookingSessions;
    private DoubleDoorListener doorListener;
    private GateRouteListener gateRouteListener;
    private GatePassageService gatePassageService;
    private RuntimeStopCoordinator runtimeStopCoordinator;
    private BukkitTask tickTask;
    private BukkitTask telemetryExportTask;
    private BukkitTask blueMapMarkerTask;
    private NpcTelemetryExporter telemetryExporter;
    private vn.heomc.livingnpc.bluemap.BlueMapMarkerService blueMapMarkers;

    private long serverTick;
    private long nextEconomyFlushTick = 1200L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        YamlConfiguration defaults = bundledDefaults();
        ConfigSchemaMigration.Result configResult = ConfigSchemaMigration.migrate(
                new java.io.File(getDataFolder(), "config.yml"), defaults, getLogger());
        if (configResult == ConfigSchemaMigration.Result.INVALID
                || configResult == ConfigSchemaMigration.Result.UNSUPPORTED) {
            getLogger().severe("LivingNPC config-dependent runtime disabled; fix config.yml and restart plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        reloadConfig();
        if (!new java.io.File(getDataFolder(), "profiles.yml").exists()) saveResource("profiles.yml", false);
        if (!new java.io.File(getDataFolder(), "prices.yml").exists()) saveResource("prices.yml", false);
        if (!new java.io.File(getDataFolder(), "recipes.yml").exists()) saveResource("recipes.yml", false);
        config = LivingNpcConfig.load(getConfig());
        geminiSettings = GeminiSettings.load(getConfig());
        profiles = new ProfileRegistry(getDataFolder());
        economy = new NpcEconomy(
                new NpcEconomyStore(getDataFolder(), getLogger()),
                new NpcPriceBook(getDataFolder()),
                config,
                getLogger());
        NavigationDiagnostics.initialize(getLogger(), economy);
        villageStore = new VillageStore(getDataFolder(), getLogger());
        mutationPolicy = new WorldMutationPolicy(
                getServer().getPluginManager(), getConfig().getBoolean("protection.require-worldguard", true));
        recipes = new ProductionRecipeRegistry(getDataFolder(), getLogger());
        miningRestorations = new MiningRestorationStore(getDataFolder(), getLogger());
        cookingSessions = new CookingSessionStore(getDataFolder(), getLogger());
        blueMapMarkers = new vn.heomc.livingnpc.bluemap.BlueMapMarkerService(getServer().getPluginManager(), getLogger());

        manager = new FarmerManager(
                new FarmerStore(getDataFolder(), getLogger()), economy, mutationPolicy, villageStore, config);
        needsManager = new NeedsManager(manager, new NeedsStore(getDataFolder(), getLogger()));
        merchantManager = new MerchantManager(manager, villageStore);
        visitorManager = new VisitorManager(villageStore, economy, merchantManager);
        rancherManager = new RancherManager(manager, economy, villageStore);
        fisherManager = new FisherManager(manager, economy, villageStore);
        civilProfessionManager = new CivilProfessionManager(
                manager, economy, villageStore, mutationPolicy, recipes, miningRestorations);
        professionMonitor = new ProfessionMonitor(
                manager, villageStore, rancherManager, fisherManager,
                civilProfessionManager, merchantManager, getLogger());
        if (ReleasePolicy.seasonTwoRuntimesEnabled()) {
            getServer().getPluginManager().registerEvents(rancherManager, this);
        }
        residentGui = new ResidentGui(this);
        getServer().getPluginManager().registerEvents(residentGui, this);
        getServer().getPluginManager().registerEvents(new LinkedBlockListener(this), this);

        doorListener = new DoubleDoorListener(this);
        gatePassageService = new GatePassageService(doorListener.passageCoordinator());
        gateRouteListener = new GateRouteListener(this, doorListener.passageCoordinator());
        getServer().getPluginManager().registerEvents(doorListener, this);
        getServer().getPluginManager().registerEvents(gateRouteListener, this);
        if (ReleasePolicy.seasonNineRuntimesEnabled()) {
            getServer().getPluginManager().registerEvents(new CookingApplianceLockListener(cookingSessions), this);
        }
        runtimeStopCoordinator = new RuntimeStopCoordinator(getLogger(), java.util.List.of(
                new RuntimeStopCoordinator.Cleanup("door-examiner", LivingDoorExaminer::resume),
                new RuntimeStopCoordinator.Cleanup("doors", doorListener::resume),
                new RuntimeStopCoordinator.Cleanup("gate-route-listener", gateRouteListener::resume),
                new RuntimeStopCoordinator.Cleanup("gate-passage", () -> {
                    if (gatePassageService != null) gatePassageService.resume();
                })),
                java.util.List.of(
                new RuntimeStopCoordinator.Cleanup("door-examiner", LivingDoorExaminer::shutdown),
                new RuntimeStopCoordinator.Cleanup("doors", doorListener::shutdown),
                new RuntimeStopCoordinator.Cleanup("gate-route-listener", gateRouteListener::shutdown),
                new RuntimeStopCoordinator.Cleanup("gate-passage", () -> {
                    if (gatePassageService != null) gatePassageService.shutdown();
                }),
                new RuntimeStopCoordinator.Cleanup("visitors", visitorManager::shutdown),
                new RuntimeStopCoordinator.Cleanup("ranchers", rancherManager::shutdown),
                new RuntimeStopCoordinator.Cleanup("fishers", fisherManager::shutdown),
                new RuntimeStopCoordinator.Cleanup("civil-professions", civilProfessionManager::shutdown),
                new RuntimeStopCoordinator.Cleanup("merchants", merchantManager::shutdown),
                new RuntimeStopCoordinator.Cleanup("residents", manager::shutdown),
                new RuntimeStopCoordinator.Cleanup("needs", needsManager::shutdown),
                new RuntimeStopCoordinator.Cleanup("economy", economy::flush)));

        LivingNpcCommand commandHandler = new LivingNpcCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("livingnpc"));
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        startTickTask();
        startTelemetryExport();
        startBlueMapMarkers();
        getLogger().info("LivingNPC Season " + ReleasePolicy.SEASON
                + " enabled; task runtime requires valid loaded world/chunks, not nearby players.");
        if (!mutationPolicy.available()) {
            getLogger().warning("WorldGuard is unavailable; world mutations are fail-closed.");
        }
    }

    @Override
    public void onDisable() {
        cancelTickTask();
        cancelTelemetryExport();
        cancelBlueMapMarkers();
        if (runtimeStopCoordinator != null) runtimeStopCoordinator.stop();
    }

    FarmerManager manager() {
        return manager;
    }

    LivingNpcConfig config() {
        return config;
    }

    ProfileRegistry profiles() {
        return profiles;
    }

    ResidentGui residentGui() {
        return residentGui;
    }

    NpcEconomy economy() {
        return economy;
    }

    GeminiSettings geminiSettings() {
        return geminiSettings;
    }

    VillageStore villages() {
        return villageStore;
    }

    boolean hasMiningRegion(org.bukkit.Location location) {
        return mutationPolicy != null && mutationPolicy.hasMiningRegion(location);
    }

    VisitorManager visitors() {
        return visitorManager;
    }

    FisherManager fishers() {
        return fisherManager;
    }

    RancherManager ranchers() {
        return rancherManager;
    }

    CivilProfessionManager civilProfessions() {
        return civilProfessionManager;
    }

    MerchantManager merchants() {
        return merchantManager;
    }

    ProfessionMonitor professionMonitor() {
        return professionMonitor;
    }

    String telemetrySnapshotJson() {
        NpcTelemetrySnapshot navigation = NavigationDiagnostics.shared().snapshot();
        TelemetryExportSettings settings = config.telemetryExport();
        NpcTelemetryEconomySnapshot economySnapshot = settings.economyEnabled()
                ? NpcTelemetrySnapshotCapture.economy(villageStore, manager, economy)
                : null;
        NpcTelemetryVisitors visitorSnapshot = settings.visitorsEnabled()
                ? visitorManager.telemetrySnapshot(config.visitors())
                : null;
        return NpcTelemetryJson.toJson(new NpcTelemetrySnapshot(
                navigation.schemaVersion(), navigation.capacity(), navigation.totalRecorded(), navigation.events(),
                economySnapshot, visitorSnapshot, telemetryGates()));
    }

    NpcTelemetryExportStatus telemetryExportStatus() {
        if (telemetryExporter == null) {
            return NpcTelemetryExportStatus.disabled(config == null ? "telemetry/latest.json" : config.telemetryExport().file());
        }
        return telemetryExporter.status();
    }

    boolean setVisitorsEnabled(boolean enabled) {
        getConfig().set("visitors.enabled", enabled);
        saveConfig();
        config = LivingNpcConfig.load(getConfig());
        return config.visitors().enabled() == enabled;
    }

    java.util.List<String> reloadPluginConfig() {
        reloadConfig();
        config = LivingNpcConfig.load(getConfig());
        geminiSettings = GeminiSettings.load(getConfig());
        profiles.reload(getDataFolder());
        economy.reloadPrices(getDataFolder());
        civilProfessionManager.reloadRecipes();
        manager.setConfig(config);
        economy.setConfig(config);
        startTickTask();
        startTelemetryExport();
        startBlueMapMarkers();

        boolean newRequireWorldGuard = getConfig().getBoolean("protection.require-worldguard", true);
        return mutationPolicy.restartRequiredReasons(getServer().getPluginManager(), newRequireWorldGuard);
    }

    private void startTickTask() {
        cancelTickTask();
        if (!getConfig().getBoolean("runtime.enabled", true)) {
            runtimeStopCoordinator.stop();
            getLogger().warning("LivingNPC runtime is disabled; NPC data and admin commands remain available.");
            return;
        }
        runtimeStopCoordinator.start();
        tickTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> {
                    serverTick += config.tickInterval();

                    runTickStep("residents", () -> manager.tick(serverTick));
                    runTickStep("door-recovery", doorListener::recoverObstructedManagedNpcs);
                    runTickStep("needs", () -> needsManager.tick(serverTick, config.needs()));
                    if (ReleasePolicy.seasonTwoRuntimesEnabled()) {
                        runTickStep("ranchers", () -> rancherManager.tick(serverTick, config));
                        runTickStep("fishers", () -> fisherManager.tick(serverTick, config));
                    }
                    if (ReleasePolicy.seasonFourRuntimesEnabled()) {
                        runTickStep("civil-professions", () -> civilProfessionManager.tick(serverTick, config));
                    }
                    runTickStep("mining-restorations", () -> miningRestorations.tick(System.currentTimeMillis(), 8));
                    if (ReleasePolicy.seasonThreeRuntimesEnabled()) {
                        runTickStep("merchants", () -> merchantManager.tick(serverTick, config));
                        runTickStep("visitors", () -> visitorManager.tick(serverTick, config));
                    }
                    runTickStep("profession-monitor", () -> professionMonitor.tick(serverTick, config));
                    if (serverTick >= nextEconomyFlushTick) {
                        runTickStep("economy-flush", economy::flush);
                        nextEconomyFlushTick = serverTick + 1200L;
                    }
                },
                config.tickInterval(),
                config.tickInterval());
    }

    private void cancelTickTask() {
        if (tickTask == null) return;
        tickTask.cancel();
        tickTask = null;
    }

    private void startTelemetryExport() {
        cancelTelemetryExport();
        TelemetryExportSettings settings = config.telemetryExport();
        if (!settings.enabled()) return;
        try {
            telemetryExporter = new NpcTelemetryExporter(
                    getDataFolder(), settings.file(),
                    action -> getServer().getScheduler().runTaskAsynchronously(this, action),
                    getLogger());
        } catch (IllegalArgumentException exception) {
            getLogger().warning("LivingNPC telemetry export disabled: " + exception.getMessage());
            return;
        }
        telemetryExportTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> runTickStep("telemetry-export", () -> telemetryExporter.exportSnapshot(telemetrySnapshotJson())),
                settings.intervalTicks(),
                settings.intervalTicks());
    }

    private void cancelTelemetryExport() {
        if (telemetryExportTask != null) {
            telemetryExportTask.cancel();
            telemetryExportTask = null;
        }
        if (telemetryExporter != null) {
            telemetryExporter.cancel();
            telemetryExporter = null;
        }
    }

    private void startBlueMapMarkers() {
        cancelBlueMapMarkers();
        vn.heomc.livingnpc.bluemap.BlueMapSettings settings = config.blueMapMarkers();
        if (!settings.enabled()) {
            getLogger().info("LivingNPC BlueMap markers disabled by config.");
            return;
        }
        blueMapMarkerTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> runTickStep("bluemap-markers", () -> blueMapMarkers.update(
                        telemetrySnapshot(), serverTick, config.blueMapMarkers())),
                settings.intervalTicks(),
                settings.intervalTicks());
        getLogger().info("LivingNPC BlueMap markers scheduled every " + settings.intervalTicks() + " ticks.");
    }

    private void cancelBlueMapMarkers() {
        if (blueMapMarkerTask != null) {
            blueMapMarkerTask.cancel();
            blueMapMarkerTask = null;
        }
        if (blueMapMarkers != null) blueMapMarkers.clear();
    }

    private NpcTelemetrySnapshot telemetrySnapshot() {
        NpcTelemetrySnapshot navigation = NavigationDiagnostics.shared().snapshot();
        return new NpcTelemetrySnapshot(
                navigation.schemaVersion(), navigation.capacity(), navigation.totalRecorded(), navigation.events(),
                config.telemetryExport().economyEnabled()
                        ? NpcTelemetrySnapshotCapture.economy(villageStore, manager, economy) : null,
                config.telemetryExport().visitorsEnabled()
                        ? visitorManager.telemetrySnapshot(config.visitors()) : null,
                telemetryGates());
    }

    private List<NpcTelemetryGate> telemetryGates() {
        Map<String, NpcTelemetryGate> gates = new LinkedHashMap<>();
        for (VillageDefinition village : villageStore.villages()) {
            List<NavigationGate> configured = village.navigationGates();
            for (int index = 0; index < configured.size() && index < 32; index++) {
                NavigationGate configuredGate = configured.get(index);
                if (configuredGate == null) continue;
                StoredLocation location = configuredGate.location();
                int x = (int) Math.floor(location.x());
                int y = (int) Math.floor(location.y());
                int z = (int) Math.floor(location.z());
                String coordinate = location.world() + ':' + x + ':' + y + ':' + z;
                String id = village.id() + '-' + index + '-' + location.world() + '-' + x + '-' + y + '-' + z;
                gates.putIfAbsent(coordinate, telemetryGate(id, location, x, y, z, configuredGate.accessClass()));
            }
        }
        return List.copyOf(gates.values());
    }

    private NpcTelemetryGate telemetryGate(String id, StoredLocation location, int x, int y, int z, String action) {
        World world = findWorld(location.world());
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return new NpcTelemetryGate(id, location.world(), x, y, z, null, null,
                    "UNKNOWN_UNAVAILABLE", action, serverTick);
        }
        Block block = world.getBlockAt(x, y, z);
        Material material = block.getType();
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof Gate gate) {
            return new NpcTelemetryGate(id, location.world(), x, y, z, material.name(), gate.isOpen(),
                    gate.isOpen() ? "OPEN" : "CLOSED", action, serverTick);
        }
        if (data instanceof Openable openable) {
            return new NpcTelemetryGate(id, location.world(), x, y, z, material.name(), openable.isOpen(),
                    openable.isOpen() ? "OPEN" : "CLOSED", action, serverTick);
        }
        return new NpcTelemetryGate(id, location.world(), x, y, z, material.name(), null,
                "UNKNOWN_NOT_GATE", action, serverTick);
    }

    private static World findWorld(String configuredName) {
        if (configuredName == null || configuredName.isBlank()) return null;
        World exact = Bukkit.getWorld(configuredName);
        if (exact != null) return exact;
        return Bukkit.getWorlds().stream()
                .filter(world -> world.getName().equalsIgnoreCase(configuredName))
                .findFirst()
                .orElse(null);
    }

    private YamlConfiguration bundledDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) return defaults;
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return defaults;
        } catch (Exception exception) {
            throw new IllegalStateException("Bundled config.yml is invalid", exception);
        }
    }

    private void runTickStep(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "LivingNPC tick step failed: " + step + "; it will retry next tick", exception);
        }
    }
}
