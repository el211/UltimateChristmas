package fr.elias.ultimateChristmas.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Msg {

    public static void info(String msg) {
        Bukkit.getConsoleSender().sendMessage("§7[§cultimateChristmas§7] " + msg);
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(color(msg));
    }

    public static void player(Player p, String msg) {
        p.sendMessage(color(msg));
    }

    public static String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }
}
