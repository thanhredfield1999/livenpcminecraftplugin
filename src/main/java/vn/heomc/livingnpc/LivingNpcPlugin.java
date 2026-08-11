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
    private CombatManager combatManager;
    private BukkitTask tickTask;
    private long serverTick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("profiles.yml", false);
        saveResource("prices.yml", false);
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
        manager = new FarmerManager(
                new FarmerStore(getDataFolder(), getLogger()), economy, mutationPolicy, villageStore, config);
        combatManager = new CombatManager(new CombatArenaStore(getDataFolder(), getLogger()), manager, economy);
        getServer().getPluginManager().registerEvents(combatManager, this);
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
        if (combatManager != null) {
            combatManager.shutdown();
        }
        if (manager != null) {
            manager.save();
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

    CombatManager combat() {
        return combatManager;
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
                    combatManager.tick(serverTick);
                    manager.tick(serverTick);
                    if (serverTick % 1200L == 0L) {
                        economy.flush();
                    }
                },
                config.tickInterval(),
                config.tickInterval());
    }
}
