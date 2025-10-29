package fr.elias.ultimateChristmas.commands;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.economy.ShardShopGUI;
import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ShardsCommand implements CommandExecutor {

    private final UltimateChristmas plugin;
    private final ShardManager shardManager;
    private final ShardShopGUI shardShopGUI;

    public ShardsCommand(UltimateChristmas plugin,
                         ShardManager shardManager,
                         ShardShopGUI shardShopGUI) {
        this.plugin = plugin;
        this.shardManager = shardManager;
        this.shardShopGUI = shardShopGUI;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {

        // /shards                -> show your balance (player only)
        // /shards shop           -> open shop (player only)
        // /shards give <player> <amount>  -> console/Santa/etc can run
        // /shards set  <player> <amount>  -> admin command to force balance

        // GIVE / SET: works from console OR in-game with permission
        if (args.length >= 1 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set"))) {

            boolean isGive = args[0].equalsIgnoreCase("give");
            boolean isSet  = args[0].equalsIgnoreCase("set");

            if (args.length < 3) {
                sender.sendMessage("§cUsage: /shards " + (isGive ? "give" : "set") + " <player> <amount>");
                return true;
            }

            String targetName = args[1];
            String amountRaw = args[2];

            int amount;
            try {
                amount = Integer.parseInt(amountRaw);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cAmount must be a number.");
                return true;
            }

            if (amount <= 0 && isGive) {
                sender.sendMessage("§cAmount must be > 0.");
                return true;
            }
            if (amount < 0 && isSet) {
                amount = 0;
            }

            OfflinePlayer target = resolveOfflinePlayer(targetName);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: §f" + targetName);
                return true;
            }

            UUID uuid = target.getUniqueId();

            if (isGive) {
                int before = shardManager.getShards(uuid);
                shardManager.addShards(uuid, amount);
                int after  = shardManager.getShards(uuid);

                // DEBUG to console every time shards are granted
                plugin.getLogger().info("[SHARDS] Added " + amount + " shards to " + target.getName()
                        + " (" + before + " -> " + after + ") by " + sender.getName());

                sender.sendMessage("§aGave §f" + amount + "§a shards to §f" + target.getName()
                        + "§a. New balance: §f" + after);

                if (target.isOnline()) {
                    Player online = target.getPlayer();
                    if (online != null) {
                        Msg.player(online,
                                "&aYou received &f" + amount + " &ashards! New balance: &f" + after);
                    }
                }
                return true;
            }

            if (isSet) {
                shardManager.setShards(uuid, amount);

                plugin.getLogger().info("[SHARDS] Set shards of " + target.getName()
                        + " to " + amount + " by " + sender.getName());

                sender.sendMessage("§eSet §f" + target.getName() + "§e to §f" + amount + "§e shards.");
                if (target.isOnline()) {
                    Player online = target.getPlayer();
                    if (online != null) {
                        Msg.player(online,
                                "&eYour shard balance was set to &f" + amount + "&e.");
                    }
                }
                return true;
            }

            return true;
        }

        // /shards and /shards shop are player-only
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("shop")) {
            if (!p.hasPermission("ultimateChristmas.shop.use")) {
                Msg.player(p, "&cNo permission.");
                return true;
            }
            shardShopGUI.open(p);
            return true;
        }

        int amount = shardManager.getShards(p.getUniqueId());
        Msg.player(
                p,
                "&aYou have &f" + amount + "&a shards.\n&7Use &f/shards shop &7to spend them."
        );
        return true;
    }

    /**
     * Safely resolve online OR offline players without throwing.
     */
    private OfflinePlayer resolveOfflinePlayer(String name) {
        // First try exact online match
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        // Fallback: let Bukkit give us an OfflinePlayer handle
        // (This will create a stub even if they've never joined, which is fine for saving balances)
        return Bukkit.getOfflinePlayer(name);
    }
}
