package fr.elias.ultimateChristmas.commands;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.music.MusicManager;
import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayFestiveMusicCommand implements CommandExecutor {

    private final MusicManager musicManager;

    public PlayFestiveMusicCommand(UltimateChristmas plugin,
                                   MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can listen to music.");
            return true;
        }

        if (!p.hasPermission("ultimateChristmas.music.play")) {
            Msg.player(p, "&cNo permission.");
            return true;
        }

        if (args.length == 0) {
            Msg.player(p, "&e/playchristmas <track|stop>\n&7Example: /playchristmas carol");
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            musicManager.stopFor(p);
            Msg.player(p, "&cMusic stopped.");
            return true;
        }

        musicManager.playTrack(p, args[0]);
        return true;
    }
}
