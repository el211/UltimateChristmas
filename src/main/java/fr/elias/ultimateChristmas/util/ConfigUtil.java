package fr.elias.ultimateChristmas.util;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigUtil {

    private static final Map<String, FileConfiguration> map = new HashMap<>();

    public static void load(UltimateChristmas plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        map.put(name, cfg);
    }

    public static FileConfiguration get(UltimateChristmas plugin, String name) {
        if ("config.yml".equalsIgnoreCase(name)) {
            return plugin.getConfig();
        }
        return map.get(name);
    }

    public static void reloadAll(UltimateChristmas plugin) {
        plugin.reloadConfig();
        Map<String, FileConfiguration> before = new HashMap<>(map);
        map.clear();
        for (String key : before.keySet()) {
            load(plugin, key);
        }
    }
}
