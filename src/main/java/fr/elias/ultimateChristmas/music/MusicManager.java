package fr.elias.ultimateChristmas.music;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MusicManager {

    private final UltimateChristmas plugin;
    private final Map<Player, RadioSongPlayer> playing = new HashMap<>();

    public MusicManager(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    public boolean playTrack(Player p, String trackId) {
        FileConfiguration cfg = plugin.getConfig("music.yml");

        String base = "tracks." + trackId;
        if (!cfg.isConfigurationSection(base)) {
            Msg.player(p, "&cUnknown track.");
            return false;
        }

        String fileName = cfg.getString(base + ".file");
        if (fileName == null) {
            Msg.player(p, "&cTrack misconfigured.");
            return false;
        }

        File songFile = new File(plugin.getDataFolder(), "music/" + fileName);
        if (!songFile.exists()) {
            Msg.player(p, "&cSong file not found on server: " + fileName);
            return false;
        }

        Song song = NBSDecoder.parse(songFile);

        // kill old
        stopFor(p);

        RadioSongPlayer rsp = new RadioSongPlayer(song);
        rsp.setAutoDestroy(true);
        rsp.addPlayer(p);
        rsp.setPlaying(true);

        playing.put(p, rsp);

        String name = cfg.getString(base + ".display_name", trackId);
        Msg.player(p, "&aPlaying &f" + name + "&a!");
        return true;
    }

    public void stopFor(Player p) {
        RadioSongPlayer old = playing.remove(p);
        if (old != null) {
            old.setPlaying(false);
            old.destroy();
        }
    }

    public void stopAll() {
        for (RadioSongPlayer rsp : playing.values()) {
            rsp.setPlaying(false);
            rsp.destroy();
        }
        playing.clear();
    }
}
