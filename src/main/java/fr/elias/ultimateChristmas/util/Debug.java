// File: src/main/java/fr/elias/ultimateChristmas/util/Debug.java
package fr.elias.ultimateChristmas.util;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;

public final class Debug {
    private static boolean enabled = false;
    private static String prefix = "§7[§cultimateChristmas:DEBUG§7] ";

    private Debug() {}

    public static void init(UltimateChristmas plugin) {
        enabled = plugin.getConfig().getBoolean("debug", false);
        if (enabled) {
            info("Debug logging is ENABLED.");
        }
    }

    public static boolean on() { return enabled; }

    public static void info(String msg) {
        if (enabled) Bukkit.getConsoleSender().sendMessage(prefix + "§f" + msg);
    }

    public static void warn(String msg) {
        if (enabled) Bukkit.getConsoleSender().sendMessage(prefix + "§eWARN: §f" + msg);
    }

    public static void error(String msg) {
        Bukkit.getConsoleSender().sendMessage(prefix + "§cERROR: §f" + msg);
    }
}
