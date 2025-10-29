package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.economy.ShardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ShardManager shardManager;

    public PlayerListener(UltimateChristmas plugin) {
        // Assuming your main plugin class has a getShardManager() method
        this.shardManager = plugin.getShardManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // CRITICAL: Reload the data config on join.
        // This ensures the ShardManager reads the latest file content
        // in case the file was changed by other plugins or commands.
        // The ShardManager must expose a reload method.
        shardManager.reloadConfig();

        // No need to load specific data, as the ShardManager already loads from file on demand (getShards)
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // No explicit action needed here for file-based saving,
        // as changes are saved immediately in ShardManager.addShards().
        // If you were using an in-memory map, you would save here.
    }
}