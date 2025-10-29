package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.util.Msg;
import io.lumine.mythic.bukkit.MythicBukkit;           // <-- direct import
import io.lumine.mythic.core.mobs.ActiveMob;           // <-- direct import
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class EntityKillListener implements Listener {

    private final UltimateChristmas plugin;
    private final ShardManager shardManager;
    private final Random random = new Random();

    private final boolean mythicInstalled;
    private final boolean mythicEnabledInConfig;

    public EntityKillListener(UltimateChristmas plugin, ShardManager shardManager) {
        this.plugin = plugin;
        this.shardManager = shardManager;

        this.mythicInstalled = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        FileConfiguration cfg = plugin.getConfig("shards.yml");
        this.mythicEnabledInConfig = cfg.getBoolean("enable-mythicmobs", false);

        if (mythicEnabledInConfig && !mythicInstalled) {
            plugin.getLogger().warning("[UltimateChristmas][SHARDS] enable-mythicmobs=true but MythicMobs plugin not found. Falling back to vanilla mobs only.");
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        FileConfiguration cfg = plugin.getConfig("shards.yml");
        if (cfg == null) return;

        LivingEntity dead = e.getEntity();
        UUID killerUUID = killer.getUniqueId();

        boolean rewarded = false;

        // 1) MythicMobs first (if allowed)
        if (mythicEnabledInConfig && mythicInstalled) {
            try {
                Optional<ActiveMob> opt = MythicBukkit.inst()
                        .getMobManager()
                        .getActiveMob(dead.getUniqueId());

                if (opt.isPresent()) {
                    ActiveMob am = opt.get();
                    String internalName = am.getType().getInternalName(); // ex "FrostGiant"

                    String mmPath = "mythicmobs." + internalName;
                    rewarded = tryRewardFromPath(
                            cfg,
                            mmPath,
                            killerUUID,
                            killer.getName(),
                            "mythicmob:" + internalName
                    );
                }
            } catch (Throwable ex) {
                plugin.getLogger().warning("[UltimateChristmas][SHARDS] MythicMobs hook errored: " + ex);
            }
        }

        // 2) Vanilla mob fallback
        if (!rewarded) {
            EntityType type = dead.getType();
            String lower = "mobs." + type.name().toLowerCase(); // mobs.zombie
            String upper = "mobs." + type.name();                // mobs.ZOMBIE
            boolean r1 = tryRewardFromPath(cfg, lower, killerUUID, killer.getName(), "mob:" + type.name());
            boolean r2 = tryRewardFromPath(cfg, upper, killerUUID, killer.getName(), "mob:" + type.name());
            rewarded = r1 || r2;
        }
    }

    private boolean tryRewardFromPath(FileConfiguration cfg,
                                      String path,
                                      UUID playerUUID,
                                      String playerName,
                                      String debugSource) {
        if (!cfg.isConfigurationSection(path)) return false;

        int shards = cfg.getInt(path + ".shards", 0);
        double chance = cfg.getDouble(path + ".chance", 0.0);

        plugin.getLogger().fine("[UltimateChristmas][SHARDS] Kill match " + debugSource
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
