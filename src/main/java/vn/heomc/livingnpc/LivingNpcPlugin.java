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
    private BukkitTask tickTask;
    private long serverTick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "profiles.yml").exists()) saveResource("profiles.yml", false);
        if (!new java.io.File(getDataFolder(), "prices.yml").exists()) saveResource("prices.yml", false);
        config = LivingNpcConfig.load(getConfig());
        geminiSettings = GeminiSettings.load(getConfig());
        profiles = new ProfileRegistry(getDataFolder());
        economy = new NpcEconomy(
                new NpcEconomyStore(getDataFolder(), getLogger()),
                new NpcPriceBook(getDataFolder()),
                config);
        villageStore = new VillageStore(getDataFolder(), getLogger());
        mutationPolicy = new WorldMutationPolicy(
                getServer().getPluginManager(), getConfig().getBoolean("protection.require-worldguard", true));
        // Recover blocks left by versions that physically removed mining targets.
        new MiningRestorationStore(getDataFolder(), getLogger());
        manager = new FarmerManager(
                new FarmerStore(getDataFolder(), getLogger()), economy, mutationPolicy, villageStore, config);
        merchantManager = new MerchantManager(manager, villageStore);
        visitorManager = new VisitorManager(villageStore, economy, merchantManager);
        rancherManager = new RancherManager(manager, economy, villageStore);
        fisherManager = new FisherManager(manager, economy, villageStore);
        civilProfessionManager = new CivilProfessionManager(manager, economy, villageStore, mutationPolicy);
        professionMonitor = new ProfessionMonitor(
                manager, villageStore, rancherManager, fisherManager,
                civilProfessionManager, merchantManager, getLogger());
        getServer().getPluginManager().registerEvents(rancherManager, this);
        residentGui = new ResidentGui(this);
        getServer().getPluginManager().registerEvents(residentGui, this);

        LivingNpcCommand commandHandler = new LivingNpcCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("livingnpc"));
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        startTickTask();
        getLogger().info("LivingNPC enabled with " + config.activationRange() + " block activation range.");
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
        manager.setConfig(config);
        startTickTask();
    }

    private void startTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> {
                    serverTick += config.tickInterval();
                    manager.tick(serverTick);
                    rancherManager.tick(serverTick, config);
                    fisherManager.tick(serverTick, config);
                    civilProfessionManager.tick(serverTick, config);
                    merchantManager.tick(serverTick, config);
                    visitorManager.tick(serverTick, config);
                    professionMonitor.tick(serverTick, config);
                    if (serverTick % 1200L == 0L) {
                        economy.flush();
                    }
                },
                config.tickInterval(),
                config.tickInterval());
    }
}
