package fr.elias.ultimateChristmas.economy;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ShardManager {

    private final UltimateChristmas plugin;
    private final File dataFile;
    private FileConfiguration data; // This must be reloaded, so it remains non-final/re-assignable

    public ShardManager(UltimateChristmas plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");

        // Ensure the data folder exists (a good practice)
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Create the file if it doesn't exist
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create playerdata.yml!");
                e.printStackTrace();
            }
        }
        // Load the file configuration immediately
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Retrieves the shard balance for a given player UUID.
     * @param uuid The UUID of the player.
     * @return The current shard balance, defaults to 0.
     */
    public int getShards(UUID uuid) {
        // Shards are stored directly under the UUID
        return data.getInt(uuid.toString(), 0);
    }

    /**
     * Retrieves the shard balance for a given OfflinePlayer.
     * @param p The OfflinePlayer.
     * @return The current shard balance, defaults to 0.
     */
    public int getShards(OfflinePlayer p) {
        return getShards(p.getUniqueId());
    }

    /**
     * Formats the player's balance as a String.
     * @param p The OfflinePlayer.
     * @return The balance as a String.
     */
    public String formatBalance(OfflinePlayer p) {
        return String.valueOf(getShards(p));
    }

    /**
     * Adds shards to a player's balance and saves the file.
     * @param uuid The UUID of the player.
     * @param amount The amount to add.
     */
    public void addShards(UUID uuid, int amount) {
        if (amount <= 0) return;
        int cur = getShards(uuid);
        data.set(uuid.toString(), cur + amount);
        save();
    }

    /**
     * Adds shards to a player's balance and saves the file.
     * @param p The OfflinePlayer.
     * @param amount The amount to add.
     */
    public void addShards(OfflinePlayer p, int amount) {
        addShards(p.getUniqueId(), amount);
    }

    /**
     * Removes shards from a player's balance and saves the file.
     * @param uuid The UUID of the player.
     * @param amount The amount to remove.
     * @return true if the removal was successful (player had enough shards), false otherwise.
     */
    public boolean removeShards(UUID uuid, int amount) {
        if (amount <= 0) return true; // Treat removing 0 or less as success
        int cur = getShards(uuid);
        if (cur < amount) return false;

        data.set(uuid.toString(), cur - amount);
        save();
        return true;
    }

    /**
     * Removes shards from a player's balance and saves the file.
     * @param p The OfflinePlayer.
     * @param amount The amount to remove.
     * @return true if the removal was successful, false otherwise.
     */
    public boolean removeShards(OfflinePlayer p, int amount) {
        return removeShards(p.getUniqueId(), amount);
    }

    /**
     * Sets a player's shard balance to a specific amount.
     * @param uuid The UUID of the player.
     * @param amount The new balance.
     */
    public void setShards(UUID uuid, int amount) {
        data.set(uuid.toString(), Math.max(0, amount)); // Ensure balance is never negative
        save();
    }


    /**
     * Saves the data file. Called automatically after add/remove/set operations.
     */
    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playerdata.yml: " + e.getMessage());
        }
    }

    /**
     * **[NEW METHOD]** Reloads the FileConfiguration object from the file system.
     * This is critical to ensure the server reads the latest balances when players join.
     */
    public void reloadConfig() {
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }
}