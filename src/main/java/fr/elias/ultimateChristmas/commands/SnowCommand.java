package fr.elias.ultimateChristmas.commands;

import fr.elias.ultimateChristmas.effects.SnowEffectManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SnowCommand implements CommandExecutor {

    private final SnowEffectManager snowManager;

    public SnowCommand(SnowEffectManager manager) {
        this.snowManager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        snowManager.toggleSnowCommand(p);
        return true;
    }
}
