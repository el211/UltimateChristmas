package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.hooks.MythicMobsHook;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Random;
import java.util.UUID;

public class EntityKillListener implements Listener {
    private final UltimateChristmas plugin;
    private final ShardManager shardManager;
    private final Random random = new Random();
    private final boolean mythicEnabledInCfg;

    public EntityKillListener(UltimateChristmas plugin, ShardManager shardManager) {
        this.plugin = plugin;
        this.shardManager = shardManager;
        FileConfiguration cfg = plugin.getConfig("shards.yml");
        this.mythicEnabledInCfg = cfg.getBoolean("enable-mythicmobs", true);
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        FileConfiguration cfg = plugin.getConfig("shards.yml");
        UUID uuid = killer.getUniqueId();

        // 1) Vanilla mob
        EntityType type = e.getEntityType();
        String lower = "mobs." + type.name().toLowerCase();
        String upper = "mobs." + type.name();
        if (tryGiveFromPath(uuid, cfg, lower, upper, "mob:" + type.name())) return;

        // 2) MythicMobs internal name (exact case)
        if (mythicEnabledInCfg && MythicMobsHook.isPresent()) {
            String internal = MythicMobsHook.getInternalName(e.getEntity());
            if (internal != null) {
                String mmPath = "mythicmobs." + internal;
                tryGiveFromPath(uuid, cfg, mmPath, null, "mythicmob:" + internal);
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
