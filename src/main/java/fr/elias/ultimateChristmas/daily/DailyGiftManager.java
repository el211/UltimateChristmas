package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.WeightedRandomPicker;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.List;
import java.util.Locale; // Needed for sound normalization

public class DailyGiftManager {

    private final UltimateChristmas plugin;

    public DailyGiftManager(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    public void redeemGift(Player p) {
        // Determine player group via LuckPerms → fallback "default"
        String group = LuckPermsHook.getPrimaryGroupOrDefault(p, "default");

        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        String basePath = "daily-gifts.rewards." + group;
        ConfigurationSection sec = cfg.getConfigurationSection(basePath);

        if (sec == null) {
            basePath = "daily-gifts.rewards.default";
            sec = cfg.getConfigurationSection(basePath);
            if (sec == null) {
                p.sendMessage("§cNo gifts configured.");
                return;
            }
        }

        // Weighted picker
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String giftId : sec.getKeys(false)) {
            int weight = sec.getInt(giftId + ".chance", 1);
            picker.add(giftId, weight);
        }

        String chosen = picker.pick();
        if (chosen == null) {
            p.sendMessage("§cGift roll failed.");
            return;
        }

        String path = basePath + "." + chosen;

        // run commands
        List<String> cmds = cfg.getStringList(path + ".commands");
        for (String raw : cmds) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    raw.replace("%player%", p.getName())
            );
        }

        // send messages
        for (String msg : cfg.getStringList(path + ".messages")) {
            p.sendMessage(msg.replace("&", "§"));
        }

        // fx
        boolean firework = cfg.getBoolean(path + ".event.firework", false);
        String soundName = cfg.getString(path + ".event.sound", "");

        if (firework) launchFirework(p.getLocation());

        if (!soundName.isEmpty()) {
            // FIX: Use the string-based playSound overload
            String normalizedSound = soundName.trim().toUpperCase(Locale.ROOT);
            // This is safer and avoids the deprecated Sound.valueOf()
            p.playSound(p.getLocation(), normalizedSound, 1f, 1f);
        }

        p.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                p.getLocation().add(0,1,0),
                20, 0.4, 0.4, 0.4, 0.01
        );
    }

    private void launchFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        // red/white firework burst
        meta.addEffect(FireworkEffect.builder()
                .withColor(org.bukkit.Color.RED)
                .withFade(org.bukkit.Color.WHITE)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }
}