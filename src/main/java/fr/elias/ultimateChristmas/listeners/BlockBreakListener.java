package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.hooks.ItemsAdderHook;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Random;
import java.util.UUID;

public class BlockBreakListener implements Listener {
    private final UltimateChristmas plugin;
    private final ShardManager shardManager;
    private final Random random = new Random();

    private final boolean itemsAdderEnabledInCfg;

    public BlockBreakListener(UltimateChristmas plugin, ShardManager shardManager) {
        this.plugin = plugin;
        this.shardManager = shardManager;
        FileConfiguration cfg = plugin.getConfig("shards.yml");
        this.itemsAdderEnabledInCfg = cfg.getBoolean("enable-itemsadder", true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;

        FileConfiguration cfg = plugin.getConfig("shards.yml");
        UUID uuid = e.getPlayer().getUniqueId();

        // 1) Vanilla block path
        Material mat = e.getBlock().getType();
        String vanillaPathLower = "blocks." + mat.name().toLowerCase();
        String vanillaPathUpper = "blocks." + mat.name();

        boolean matched = tryGiveFromPath(uuid, cfg, vanillaPathLower, vanillaPathUpper, "block:" + mat.name());
        if (matched) return;

        // 2) ItemsAdder custom block path
        if (itemsAdderEnabledInCfg && ItemsAdderHook.isPresent()) {
            String iaId = ItemsAdderHook.getNamespacedId(e.getBlock()); // e.g. "christmas:present_block"
            if (iaId != null) {
                String iaPath = "itemsadder-blocks." + iaId; // keys must be quoted in YAML
                tryGiveFromPath(uuid, cfg, iaPath, null, "ia-block:" + iaId);
            }
        }
    }

    private boolean tryGiveFromPath(UUID uuid, FileConfiguration cfg, String primary, String fallback, String debug) {
        String use = null;
        if (cfg.contains(primary)) use = primary;
        else if (fallback != null && cfg.contains(fallback)) use = fallback;
        if (use == null) return false;

        int shards = cfg.getInt(use + ".shards", 0);
        double chance = cfg.getDouble(use + ".chance", 0.0);
        if (shards <= 0 || chance <= 0) return false;

        // tolerate tiny floating errors and treat 1.0 as guaranteed
        double roll = random.nextDouble();
        if (chance >= 1.0 || roll <= chance + 1e-12) {
            shardManager.addShards(uuid, shards);
            int total = shardManager.getShards(uuid);
            String msg = plugin.getConfig().getString("messages.shards-earned",
                            "&a+ %amount% shards &7(You now have %total%)")
                    .replace("%amount%", String.valueOf(shards))
                    .replace("%total%", String.valueOf(total))
                    .replace("&", "§");
            var p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
            plugin.getLogger().fine("[SHARDS] Gave " + shards + " from " + debug + " (total=" + total + ")");
            return true;
        }
        return false;
    }
}
