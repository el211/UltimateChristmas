package fr.elias.ultimateChristmas.commands;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.santa.SantaManager;
import fr.elias.ultimateChristmas.util.ConfigUtil;
import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class SantaAdminCommand implements CommandExecutor {

    private final UltimateChristmas plugin;
    private final SantaManager santa;

    public SantaAdminCommand(UltimateChristmas plugin, SantaManager santa) {
        this.plugin = plugin;
        this.santa = santa;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("ultimateChristmas.admin")) {
            Msg.send(sender, "&cNo permission.");
            return true;
        }

        if (args.length == 0) {
            Msg.send(sender, "&e/santaadmin spawn|despawn|reload");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                santa.spawnSantaFromConfig(); // <--- CORRECTED CALL
                Msg.send(sender, "&aSanta forced spawn.");
                break;
            case "despawn":
                santa.despawnSantaIfAny();
                Msg.send(sender, "&cSanta despawned.");
                break;
            case "reload":
                ConfigUtil.reloadAll(plugin);
                Msg.send(sender, "&aConfigs reloaded.");
                break;
            default:
                Msg.send(sender, "&e/santaadmin spawn|despawn|reload");
        }
        return true;
    }
}
