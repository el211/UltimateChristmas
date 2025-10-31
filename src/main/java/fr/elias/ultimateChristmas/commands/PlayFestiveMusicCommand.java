package fr.elias.ultimateChristmas.commands;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.music.MusicManager;
import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class PlayFestiveMusicCommand implements CommandExecutor, TabCompleter {

    // Permissions (adjust if you use different nodes)
    private static final String PERM_USE = "ultimatechristmas.music";
    private static final String PERM_OTHERS = "ultimatechristmas.music.others";
    private static final String PERM_STOP = "ultimatechristmas.music.stop";

    private final UltimateChristmas plugin;
    private final MusicManager music;
    private final Logger log;
    private final boolean debug;

    public PlayFestiveMusicCommand(UltimateChristmas plugin, MusicManager musicManager) {
        this.plugin = plugin;
        this.music = musicManager;
        this.log = plugin.getLogger();
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    private void d(String s) {
        if (debug) log.info("[PlayFestiveMusic][DEBUG] " + s);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Msg.color("&eUsage: &f/" + label + " &7<trackKey|list|stop> [&7self|playerName&f]"));
        sender.sendMessage(Msg.color("&8- &flist &7: affiche les pistes disponibles"));
        sender.sendMessage(Msg.color("&8- &fstop &7: arrête la musique"));
        if (!music.isNoteBlockAvailable()) {
            sender.sendMessage(Msg.color("&cNoteBlockAPI n'est pas chargé. Installez-le et ajoutez &fsoftdepend: [NoteBlockAPI]&c."));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage(Msg.color("&cVous n'avez pas la permission."));
            return true;
        }

        // /playchristmas
        if (args.length == 0) {
            sendUsage(sender, label);
            // Quick helper showing available keys
            List<String> keys = music.getKeys();
            if (keys.isEmpty()) {
                sender.sendMessage(Msg.color("&7Aucune piste trouvée. Configurez &fmusic.yml &7→ &etracks:*"));
            } else {
                sender.sendMessage(Msg.color("&7Pistes: &f" + String.join("&7, &f", keys)));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Target selection logic
        Player explicitTarget = null;
        boolean selfOnly = false;

        if (args.length >= 2) {
            String targetArg = args[1];
            if ("self".equalsIgnoreCase(targetArg)) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Msg.color("&cSeul un joueur peut utiliser &fself&c."));
                    return true;
                }
                explicitTarget = (Player) sender;
                selfOnly = true;
            } else {
                // a player name was given
                if (!sender.hasPermission(PERM_OTHERS)) {
                    sender.sendMessage(Msg.color("&cVous n'avez pas la permission de cibler d'autres joueurs."));
                    return true;
                }
                explicitTarget = Bukkit.getPlayerExact(targetArg);
                if (explicitTarget == null) {
                    sender.sendMessage(Msg.color("&cJoueur introuvable: &f" + targetArg));
                    return true;
                }
            }
        }

        // Handle subcommands
        switch (sub) {
            case "list": {
                List<String> keys = music.getKeys();
                if (keys.isEmpty()) {
                    sender.sendMessage(Msg.color("&7Aucune piste disponible (&fmusic.yml&7)."));
                } else {
                    sender.sendMessage(Msg.color("&7Pistes: &f" + String.join("&7, &f", keys)));
                }
                return true;
            }
            case "stop": {
                if (!sender.hasPermission(PERM_STOP)) {
                    sender.sendMessage(Msg.color("&cPermission manquante: &f" + PERM_STOP));
                    return true;
                }
                // stop for self or a specific player or globally if console/no args
                if (explicitTarget != null) {
                    String msg = music.stopFor(explicitTarget);
                    sender.sendMessage(Msg.color(msg));
                } else if (sender instanceof Player p) {
                    String msg = music.stopFor(p);
                    sender.sendMessage(Msg.color(msg));
                } else {
                    // console: stop for all
                    int count = music.stopAll();
                    sender.sendMessage(Msg.color("&aArrêt de la musique pour &f" + count + " &ajoueur(s)."));
                }
                return true;
            }
            default:
                // Play a track key
                break;
        }

        // From here, sub is considered a track key
        String trackKey = sub;

        // Resolve default target (if none specified)
        Player target = explicitTarget;
        if (target == null && sender instanceof Player p) {
            // default to sender
            target = p;
            selfOnly = true;
        }

        // If still null → console must specify a player
        if (target == null) {
            sender.sendMessage(Msg.color("&cDepuis la console, précisez &f/" + label + " " + trackKey + " <player>&c."));
            return true;
        }

        d("Invoked by=" + sender.getName()
                + " trackKey=" + trackKey
                + " target=" + target.getName()
                + " selfOnly=" + selfOnly
                + " nbapiAvailable=" + music.isNoteBlockAvailable());

        String result = music.playTrack(trackKey, selfOnly ? target : null);
        sender.sendMessage(Msg.color(result));
        return true;
    }

    // ---------- TAB COMPLETE ----------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String alias,
                                      @NotNull String[] args) {

        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("list");
            base.add("stop");
            base.addAll(music.getKeys());
            String cur = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : base) {
                if (s.toLowerCase(Locale.ROOT).startsWith(cur)) out.add(s);
            }
            Collections.sort(out);
            return out;
        }

        if (args.length == 2) {
            // Suggest "self" and online players
            String cur = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("self".startsWith(cur)) out.add("self");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(cur)) out.add(p.getName());
            }
            Collections.sort(out);
            return out;
        }

        return Collections.emptyList();
    }
}
