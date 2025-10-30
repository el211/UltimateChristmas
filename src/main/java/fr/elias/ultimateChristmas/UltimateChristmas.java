package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.commands.PlayFestiveMusicCommand;
import fr.elias.ultimateChristmas.commands.SantaAdminCommand;
import fr.elias.ultimateChristmas.commands.ShardsCommand;
import fr.elias.ultimateChristmas.daily.DailyGiftCommand;
import fr.elias.ultimateChristmas.daily.DailyGiftListener;
import fr.elias.ultimateChristmas.daily.DailyGiftManager;
import fr.elias.ultimateChristmas.daily.DailyProgressStore;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.economy.ShardShopGUI;
import fr.elias.ultimateChristmas.economy.ShardShopListener;
import fr.elias.ultimateChristmas.integration.WorldGuardIntegration;
import fr.elias.ultimateChristmas.listeners.BlockBreakListener;
import fr.elias.ultimateChristmas.listeners.CustomDurabilityListener;
import fr.elias.ultimateChristmas.listeners.EntityKillListener;
import fr.elias.ultimateChristmas.listeners.PlayerListener;
import fr.elias.ultimateChristmas.listeners.SnowballHitListener;
import fr.elias.ultimateChristmas.music.MusicManager;
import fr.elias.ultimateChristmas.santa.SantaManager;
import fr.elias.ultimateChristmas.santa.SantaProtectionListener;
import fr.elias.ultimateChristmas.santa.SantaWalkController;
import fr.elias.ultimateChristmas.util.ConfigUtil;
import fr.elias.ultimateChristmas.util.Debug;
import fr.elias.ultimateChristmas.util.Msg;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInvsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class UltimateChristmas extends JavaPlugin {

    /* -------------------------------------------------
     * STATIC INSTANCE
     * ------------------------------------------------- */
    private static UltimateChristmas instance;

    /* -------------------------------------------------
     * MANAGERS / CORE COMPONENTS
     * ------------------------------------------------- */
    private ShardManager shardManager;
    private InventoryManager invManager;
    private ShardShopGUI shardShopGUI;
    private DailyGiftManager dailyGiftManager;
    private MusicManager musicManager;
    private WorldGuardIntegration wgIntegration;
    private SantaManager santaManager;

    // active pathfinder walker for Santa (Paper only)
    private SantaWalkController activeWalkController;

    // cache for capability detection
    private boolean paperPathfinderAvailable;
    private DailyProgressStore dailyProgressStore;

    /* -------------------------------------------------
     * ENABLE / DISABLE
     * ------------------------------------------------- */
    @Override
    public void onEnable() {
        instance = this;

        /*
         * 1) Load / create all configs
         */
        saveDefaultConfig();
        ConfigUtil.load(this, "shards.yml");
        ConfigUtil.load(this, "shop.yml");
        ConfigUtil.load(this, "daily_gifts.yml");
        ConfigUtil.load(this, "music.yml");
        ConfigUtil.load(this, "santa.yml");
        ConfigUtil.load(this, "snowball.yml");

        /*
         * 2) Init debug helper first so Debug.info() works everywhere
         */
        Debug.init(this);

        /*
         * 3) Detect optional server capabilities
         */
        this.paperPathfinderAvailable = checkPaperPathfinder();

        Debug.info("Server brand: " + Bukkit.getName() + " " + Bukkit.getBukkitVersion());
        Debug.info("Paper pathfinder API available: " + paperPathfinderAvailable);
        Debug.info("LibsDisguises present: " + isPluginEnabled("LibsDisguises"));
        Debug.info("WorldGuard present: " + isPluginEnabled("WorldGuard"));

        /*
         * 4) Core managers
         */

        // Shard economy
        this.shardManager = new ShardManager(this);
        // make sure we have latest persistent balances in memory
        this.shardManager.reloadConfig();

        // Daily gift claim / cooldown logic
        this.dailyGiftManager = new DailyGiftManager(this);
        this.dailyProgressStore = new DailyProgressStore(this);
        this.dailyGiftManager.setProgressStore(this.dailyProgressStore);

        // Music / festive song playback
        this.musicManager = new MusicManager(this);

        // Region leash + safety integration for Santa
        this.wgIntegration = new WorldGuardIntegration(this);

        /*
         * 5) SmartInvs / ShardShop GUI setup
         *
         * SmartInvs needs ONE global InventoryManager.
         * We create it here, init it, then pass it to ShardShopGUI.
         */
        this.invManager = new InventoryManager(this);
        this.invManager.init();

        // Some versions of SmartInvs require informing the static plugin holder.
        SmartInvsPlugin.setPlugin(this);

        // Shard shop GUI (uses invManager + shardManager)
        this.shardShopGUI = new ShardShopGUI(this, this.shardManager, this.invManager);

        /*
         * 6) Santa manager
         *    Handles spawn/despawn, disguises, walking AI, gift drops,
         *    and shard rewards.
         */
        this.santaManager = new SantaManager(this, this.wgIntegration, this.shardManager);

        /*
         * 7) Register listeners
         */
        var pm = getServer().getPluginManager();

        // earn shards from breaking blocks / killing mobs etc
        pm.registerEvents(new BlockBreakListener(this, shardManager), this);
        pm.registerEvents(new EntityKillListener(this, shardManager), this);

        // NEW: SmartInvs-based daily gift confirm/open
        pm.registerEvents(new DailyGiftListener(this, dailyGiftManager, invManager), this);

        // snowball fights / slow effect etc
        pm.registerEvents(new SnowballHitListener(this), this);

        // shop close hook (currently mostly placeholder for future cleanup)
        pm.registerEvents(new ShardShopListener(this, shardShopGUI), this);

        // join/quit etc player events, messages, etc
        pm.registerEvents(new PlayerListener(this), this);

        // protect Santa from damage / block renames / hologram bars etc
        pm.registerEvents(new SantaProtectionListener(this, santaManager), this);

        // custom “uses left” durability system
        pm.registerEvents(new CustomDurabilityListener(this), this);

        /*
         * 8) Register commands
         */
        if (getCommand("shards") != null) {
            getCommand("shards").setExecutor(
                    new ShardsCommand(this, shardManager, shardShopGUI)
            );
        }
        if (getCommand("daily") != null) {
            // FIX: use the constructor that DailyCommand actually provides
            getCommand("daily").setExecutor(
                    new fr.elias.ultimateChristmas.daily.DailyCommand(
                            invManager,
                            dailyGiftManager,
                            dailyProgressStore
                    )
            );
        }

        if (getCommand("playchristmas") != null) {
            getCommand("playchristmas").setExecutor(
                    new PlayFestiveMusicCommand(this, musicManager)
            );
        }

        if (getCommand("santaadmin") != null) {
            getCommand("santaadmin").setExecutor(
                    new SantaAdminCommand(this, santaManager)
            );
        }

        // Optional helper command to give a daily gift item (if you added it to plugin.yml)
        if (getCommand("ucgift") != null) {
            getCommand("ucgift").setExecutor(new DailyGiftCommand(this));
        }

        /*
         * 9) Start Santa's repeating task
         */
        santaManager.startScheduler();

        Msg.info("ultimateChristmas enabled. Ho ho ho!");
    }

    @Override
    public void onDisable() {
        /*
         * Graceful shutdown:
         * - Despawn Santa and cancel his timers
         * - Stop any music loops
         * - Persist shard balances
         */
        if (santaManager != null) {
            santaManager.despawnSantaIfAny();
        }

        if (musicManager != null) {
            musicManager.stopAll();
        }

        if (shardManager != null) {
            shardManager.save();
        }
        if (dailyProgressStore != null) dailyProgressStore.save();

        Msg.info("ultimateChristmas disabled.");
    }

    /* -------------------------------------------------
     * WALK CONTROLLER HOOK
     * ------------------------------------------------- */

    /**
     * Called by SantaManager when Paper async walking AI is started/stopped.
     * We keep track so we can stop an old controller if a new one replaces it.
     */
    public void setActiveWalkController(SantaWalkController controller) {
        if (this.activeWalkController != null) {
            this.activeWalkController.stopWalking();
        }
        this.activeWalkController = controller;
    }

    public DailyProgressStore getDailyProgressStore() { return dailyProgressStore; }

    /* -------------------------------------------------
     * GETTERS / HELPERS
     * ------------------------------------------------- */

    public static UltimateChristmas get() {
        return instance;
    }

    /**
     * Wrapper to get custom YMLs we loaded with ConfigUtil.load(...).
     * Example: getConfig("shop.yml"), getConfig("santa.yml")
     */
    public FileConfiguration getConfig(String fileName) {
        return ConfigUtil.get(this, fileName);
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public ShardShopGUI getShardShopGUI() {
        return shardShopGUI;
    }

    public DailyGiftManager getDailyGiftManager() {
        return dailyGiftManager;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }

    public WorldGuardIntegration getWgIntegration() {
        return wgIntegration;
    }

    public SantaManager getSantaManager() {
        return santaManager;
    }

    public InventoryManager getInvManager() {
        return invManager;
    }

    /**
     * Convenience so other plugins/listeners can ask
     * "is this entity THE Santa NPC?"
     */
    public boolean isSantaEntity(org.bukkit.entity.Entity e) {
        return (santaManager != null && santaManager.isSanta(e));
    }

    /**
     * True if server looks like Paper/Purpur and supports Mob#getPathfinder().
     * SantaManager uses this to decide walking AI vs leash fallback.
     */
    public boolean hasPaperPathfinder() {
        return paperPathfinderAvailable;
    }

    /**
     * Check if optional plugin is present & enabled (WorldGuard, LibsDisguises, etc).
     */
    private boolean isPluginEnabled(String name) {
        Plugin p = getServer().getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    /**
     * Detect whether Paper's pathfinder API exists on this server build.
     * We just reflect-check org.bukkit.entity.Mob#getPathfinder().
     */
    private boolean checkPaperPathfinder() {
        try {
            Class.forName("org.bukkit.entity.Mob").getMethod("getPathfinder");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
