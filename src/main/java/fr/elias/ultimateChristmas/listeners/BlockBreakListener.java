package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.util.Msg;
import dev.lone.itemsadder.api.CustomBlock; // <-- direct import
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Random;
import java.util.UUID;

public class BlockBreakListener implements Listener {

    private final UltimateChristmas plugin;
    private final ShardManager shardManager;
    private final Random random = new Random();

    // cache booleans so we don't check every single break
    private final boolean itemsAdderInstalled;
    private final boolean itemsAdderEnabledInConfig;

    public BlockBreakListener(UltimateChristmas plugin, ShardManager shardManager) {
        this.plugin = plugin;
        this.shardManager = shardManager;

        this.itemsAdderInstalled = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        FileConfiguration cfg = plugin.getConfig("shards.yml");
        this.itemsAdderEnabledInConfig = cfg.getBoolean("enable-itemsadder", false);

        if (itemsAdderEnabledInConfig && !itemsAdderInstalled) {
            plugin.getLogger().warning("[UltimateChristmas][SHARDS] enable-itemsadder=true but ItemsAdder plugin not found. Falling back to vanilla blocks only.");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;

        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        Block block = e.getBlock();

        FileConfiguration cfg = plugin.getConfig("shards.yml");
        if (cfg == null) return;

        boolean rewarded = false;

        // 1) ITEMSADDER block check (if allowed)
        if (itemsAdderEnabledInConfig && itemsAdderInstalled) {
            try {
                CustomBlock cb = CustomBlock.byAlreadyPlaced(block);
                if (cb != null) {
                    String namespacedId = cb.getNamespacedID(); // e.g. "christmas:present_block"
                    String path = "itemsadder-blocks." + namespacedId;
                    rewarded = tryRewardFromPath(cfg, path, uuid, p.getName(), "itemsadder-block:" + namespacedId);
                }
            } catch (Throwable ex) {
                // if ItemsAdder API explodes for any reason, log once and continue vanilla
                plugin.getLogger().warning("[UltimateChristmas][SHARDS] Error reading ItemsAdder block info: " + ex);
            }
        }

        // 2) VANILLA block check (only pay if not already rewarded by IA)
        if (!rewarded) {
            Material mat = block.getType();
            String lower = "blocks." + mat.name().toLowerCase(); // blocks.iron_ore
            String upper = "blocks." + mat.name();                // blocks.IRON_ORE
            tryRewardFromPath(cfg, lower, uuid, p.getName(), "block:" + mat.name());
            tryRewardFromPath(cfg, upper, uuid, p.getName(), "block:" + mat.name());
        }
    }

    /**
     * Attempts to pay shards if cfg[path] exists and roll succeeds.
     * Returns true if shards were actually given.
     */
    private boolean tryRewardFromPath(FileConfiguration cfg,
                                      String path,
                                      UUID playerUUID,
                                      String playerName,
                                      String debugSource) {
        if (!cfg.isConfigurationSection(path)) return false;

        int shards = cfg.getInt(path + ".shards", 0);
        double chance = cfg.getDouble(path + ".chance", 0.0);

        plugin.getLogger().fine("[UltimateChristmas][SHARDS] Block match " + debugSource
                + " -> shards=" + shards + " chance=" + chance);

        if (shards <= 0 || chance <= 0) return false;

        double roll = random.nextDouble();
        if (roll > chance) {
            plugin.getLogger().fine("[UltimateChristmas][SHARDS] " + playerName
                    + " failed roll for " + debugSource
                    + " (roll=" + roll + " > " + chance + ")");
            return false;
        }

        shardManager.addShards(playerUUID, shards);
        int total = shardManager.getShards(playerUUID);

        String msg = plugin.getConfig().getString(
                "messages.shards-earned",
                "&a+ %amount% shards &7(You now have %total%)"
        );
        msg = msg.replace("%amount%", String.valueOf(shards));
        msg = msg.replace("%total%", String.valueOf(total));

        Player p = Bukkit.getPlayer(playerUUID);
        if (p != null && p.isOnline()) {
            p.sendMessage(Msg.color(msg));
        }

        plugin.getLogger().info("[UltimateChristmas][SHARDS] Gave "
                + shards + " shards to " + playerName
                + " from " + debugSource
                + " (roll ok) total=" + total);

        return true;
    }
}
