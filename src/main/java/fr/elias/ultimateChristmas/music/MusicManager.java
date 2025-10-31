package fr.elias.ultimateChristmas.music;

import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MusicManager {
    private final Plugin plugin;
    private final Logger log;
    private final boolean debug;

    private static final String DIR_SONGS = "songs";
    private static final String DIR_MUSIC = "music";

    private static class Track {
        final String file;
        final String displayName;
        Track(String file, String displayName) {
            this.file = file;
            this.displayName = displayName;
        }
    }

    private final Map<String, Track> tracks = new LinkedHashMap<>();

    public MusicManager(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.debug = plugin.getConfig().getBoolean("debug", false);
        loadTracks();
    }

    // ---------- API utilisée par la commande ----------

    public boolean isNoteBlockAvailable() {
        try {
            var nbp = Bukkit.getPluginManager().getPlugin("NoteBlockAPI");
            boolean loaded = nbp != null && nbp.isEnabled();
            if (!loaded) return false;

            // Diag informatif (ne bloque pas)
            try {
                var api = NoteBlockAPI.getAPI();
                if (api == null && debug) {
                    log.warning("[Music][DEBUG] NoteBlockAPI.getAPI() est null mais le plugin est actif. " +
                            "C'est normal sur certains forks; on continue quand même.");
                } else if (debug) {
                    log.info("[Music][DEBUG] NoteBlockAPI.getAPI() OK");
                }
            } catch (Throwable t) {
                if (debug)
                    log.warning("[Music][DEBUG] Appel getAPI() a lancé: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }

            return true; // plugin présent et enabled → disponible
        } catch (Throwable t) {
            return false;
        }
    }

    public List<String> getKeys() {
        var list = new ArrayList<>(tracks.keySet());
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public String playTrack(String key, Player onlyTarget) {
        if (!isNoteBlockAvailable()) {
            return color("&cNoteBlockAPI n'est pas chargé. Ajoutez &fsoftdepend: [NoteBlockAPI]&c et placez le jar.");
        }

        Track track = tracks.get(key);
        if (track == null) {
            return color("&cPiste inconnue: &f" + key + "&7. Utilisez &f/playchristmas list&7.");
        }

        String displayName = track.displayName != null ? track.displayName : key;

        if (onlyTarget != null) {
            boolean ok = playRadio(onlyTarget, track.file);
            return ok ? color("&aLecture de &f" + displayName + " &a→ &f" + onlyTarget.getName())
                    : color("&cÉchec du démarrage de &f" + displayName + " &cpour &f" + onlyTarget.getName());
        }

        int okCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playRadio(p, track.file)) okCount++;
        }

        return okCount > 0
                ? color("&aLecture de &f" + displayName + " &apour &f" + okCount + " &ajoueur(s).")
                : color("&cImpossible de démarrer &f" + displayName + " &cpour les joueurs connectés.");
    }

    public String stopFor(Player p) {
        try {
            NoteBlockAPI.stopPlaying(p);
            return color("&aMusique arrêtée pour &f" + p.getName());
        } catch (Throwable t) {
            log.log(Level.WARNING, "[Music] stopFor error: " + t.getMessage(), t);
            return color("&cErreur lors de l'arrêt pour &f" + p.getName());
        }
    }

    public int stopAll() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                NoteBlockAPI.stopPlaying(p);
                count++;
            } catch (Throwable t) {
                log.log(Level.WARNING, "[Music] stopAll error for " + p.getName() + ": " + t.getMessage(), t);
            }
        }
        return count;
    }

    // ---------- Lancement bas-niveau ----------

    public boolean playRadio(Player player, String songFileName) {
        d("Invoke by " + player.getName() + " for '" + songFileName + "'");

        var nbp = Bukkit.getPluginManager().getPlugin("NoteBlockAPI");
        if (nbp == null || !nbp.isEnabled()) {
            log.warning("[Music] NoteBlockAPI not found or not enabled.");
            return false;
        }

        // Diag seulement — ne PAS retourner false si getAPI() == null
        try {
            if (NoteBlockAPI.getAPI() == null && debug) {
                log.warning("[Music][DEBUG] getAPI() == null (fork/impl). On tente quand même la lecture.");
            } else if (debug) {
                log.info("[Music][DEBUG] getAPI() OK");
            }
        } catch (Throwable ignored) {}

        d("NoteBlockAPI OK. Version=" + nbp.getDescription().getVersion());

        try {
            File baseSongs = new File(plugin.getDataFolder(), DIR_SONGS);
            File baseMusic = new File(plugin.getDataFolder(), DIR_MUSIC);
            if (!baseSongs.exists()) baseSongs.mkdirs();

            File nbs = new File(baseSongs, songFileName);
            if (!nbs.exists()) nbs = new File(baseMusic, songFileName);

            d("Resolved NBS path: " + nbs.getAbsolutePath());
            if (!nbs.exists()) {
                log.warning("[Music] NBS not found in 'songs/' or 'music/': " + songFileName);
                return false;
            }

            Song song = NBSDecoder.parse(nbs);
            if (song == null) {
                log.warning("[Music] Failed to parse NBS: " + nbs.getName());
                return false;
            }

            d("Parsed song: title=" + song.getTitle() + ", author=" + song.getAuthor()
                    + ", len=" + song.getLength() + " ticks, speed=" + song.getSpeed());

            RadioSongPlayer rsp = new RadioSongPlayer(song);
            rsp.setAutoDestroy(true);
            rsp.addPlayer(player);
            rsp.setPlaying(true);
            d("Starting RadioSongPlayer for " + player.getName());
            return true;

        } catch (Throwable t) {
            log.log(Level.WARNING, "[Music] Failed starting RadioSongPlayer: " + t.getMessage(), t);
            return false;
        }
    }

    // ---------- Internes ----------

    private void loadTracks() {
        tracks.clear();
        File musicFile = new File(plugin.getDataFolder(), "music.yml");
        YamlConfiguration cfg = musicFile.exists()
                ? YamlConfiguration.loadConfiguration(musicFile)
                : YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));

        ConfigurationSection sec = cfg.isConfigurationSection("tracks")
                ? cfg.getConfigurationSection("tracks")
                : cfg.getConfigurationSection("music.tracks");

        if (sec == null) {
            d("No 'tracks' section found.");
            return;
        }

        for (String key : sec.getKeys(false)) {
            String fileName = null;
            String display = null;

            if (sec.isString(key)) {
                fileName = sec.getString(key);
            } else if (sec.isConfigurationSection(key)) {
                ConfigurationSection node = sec.getConfigurationSection(key);
                fileName = node.getString("file");
                display = node.getString("display_name");
            }

            if (fileName == null || fileName.isBlank()) continue;
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".nbs")) fileName += ".nbs";

            tracks.put(key, new Track(fileName, display));
            d("Track: " + key + " -> " + fileName + (display != null ? " (" + display + ")" : ""));
        }
    }

    private void d(String msg) {
        if (debug) log.info("[Music][DEBUG] " + msg);
    }

    private String color(String s) {
        return s.replace('&', '§');
    }
}
