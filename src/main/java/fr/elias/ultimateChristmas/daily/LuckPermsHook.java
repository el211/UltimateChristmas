package fr.elias.ultimateChristmas.daily;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class LuckPermsHook {

    public static String getPrimaryGroupOrDefault(Player p, String def) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return def;
        }
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(p.getUniqueId());
            if (user == null) return def;
            String primary = user.getPrimaryGroup();
            if (primary == null || primary.isEmpty()) return def;
            return primary;
        } catch (Exception ex) {
            return def;
        }
    }
}
