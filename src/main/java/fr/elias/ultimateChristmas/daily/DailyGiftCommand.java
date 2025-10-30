// fr/elias/ultimateChristmas/daily/DailyGiftCommand.java
package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DailyGiftCommand implements CommandExecutor {

    private final UltimateChristmas plugin;

    public DailyGiftCommand(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§eUsage: /" + label + " give <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        target.getInventory().addItem(GiftItemFactory.buildGiftItem(cfg));
        sender.sendMessage("§aGave a Daily Gift to §f" + target.getName());
        return true;
    }
}
