package vn.heomc.livingnpc;

import java.util.Objects;
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
    private BukkitTask tickTask;
    private long serverTick;
    private long nextEconomyFlushTick = 1200L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        villageStore = new VillageStore(getDataFolder(), getLogger());
        mutationPolicy = new WorldMutationPolicy(
                getServer().getPluginManager(), getConfig().getBoolean("protection.require-worldguard", true));
        recipes = new ProductionRecipeRegistry(getDataFolder(), getLogger());
        miningRestorations = new MiningRestorationStore(getDataFolder(), getLogger());
        cookingSessions = new CookingSessionStore(getDataFolder(), getLogger());
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
        getServer().getPluginManager().registerEvents(new DoubleDoorListener(this), this);
        if (ReleasePolicy.seasonNineRuntimesEnabled()) {
            getServer().getPluginManager().registerEvents(new CookingApplianceLockListener(cookingSessions), this);
        }

        LivingNpcCommand commandHandler = new LivingNpcCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("livingnpc"));
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        startTickTask();
        getLogger().info("LivingNPC Season " + ReleasePolicy.SEASON + " enabled with "
                + config.activationRange() + " block activation range.");
        if (!mutationPolicy.available()) {
            getLogger().warning("WorldGuard is unavailable; world mutations are fail-closed.");
        }
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (manager != null) {
            manager.shutdown();
        }
        if (visitorManager != null) {
            visitorManager.shutdown();
        }
        if (rancherManager != null) {
            rancherManager.shutdown();
        }
        if (fisherManager != null) {
            fisherManager.shutdown();
        }
        if (civilProfessionManager != null) {
            civilProfessionManager.shutdown();
        }
        if (merchantManager != null) {
            merchantManager.shutdown();
        }
        if (needsManager != null) {
            needsManager.shutdown();
        }
        if (economy != null) {
            economy.flush();
        }
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

    boolean setVisitorsEnabled(boolean enabled) {
        getConfig().set("visitors.enabled", enabled);
        saveConfig();
        config = LivingNpcConfig.load(getConfig());
        return config.visitors().enabled() == enabled;
    }

    void reloadPluginConfig() {
        reloadConfig();
        config = LivingNpcConfig.load(getConfig());
        geminiSettings = GeminiSettings.load(getConfig());
        profiles.reload(getDataFolder());
        economy.reloadPrices(getDataFolder());
        civilProfessionManager.reloadRecipes();
        manager.setConfig(config);
        startTickTask();
    }

    private void startTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (!getConfig().getBoolean("runtime.enabled", true)) {
            getLogger().warning("LivingNPC runtime is disabled; NPC data and admin commands remain available.");
            return;
        }
        tickTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> {
                    serverTick += config.tickInterval();
                    runTickStep("residents", () -> manager.tick(serverTick));
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

    private void runTickStep(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "LivingNPC tick step failed: " + step + "; it will retry next tick", exception);
        }
    }
}
