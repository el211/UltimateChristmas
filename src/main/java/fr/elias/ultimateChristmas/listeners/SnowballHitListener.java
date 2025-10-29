package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.WeightedRandomPicker;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SnowballHitListener implements Listener {

    private final UltimateChristmas plugin;

    private final Map<UUID, Long> lastReward = new HashMap<>();

    public SnowballHitListener(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSnowballHit(ProjectileHitEvent e) {
        // Ensure the projectile is a Snowball
        if (!(e.getEntity() instanceof Snowball snow)) return;
        // Ensure the shooter is a Player
        if (!(snow.getShooter() instanceof Player p)) return;
        // Ensure the hit entity is a Villager
        if (!(e.getHitEntity() instanceof Villager)) return;

        if (!p.hasPermission("ultimateChristmas.snowball.reward")) return;

        FileConfiguration cfg = plugin.getConfig("snowball.yml");
        String base = "villager-shoot.rewards.default";
        ConfigurationSection sec = cfg.getConfigurationSection(base);
        if (sec == null) return;

        // Cooldown check
        long cooldown = sec.getLong("cooldown", 30L);
        long now = System.currentTimeMillis() / 1000;
        long last = lastReward.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < cooldown) {
            p.sendMessage("§cThe villager needs a break!");
            return;
        }
        lastReward.put(p.getUniqueId(), now);

        // Weighted reward picking
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String key : sec.getKeys(false)) {
            if (key.equalsIgnoreCase("cooldown")) continue;
            picker.add(key, sec.getInt(key + ".chance", 1));
        }

        String chosen = picker.pick();
        if (chosen == null) return;
        String path = base + "." + chosen;

        // Commands
        for (String cmd : cfg.getStringList(path + ".commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", p.getName()));
        }

        // Messages
        for (String msg : cfg.getStringList(path + ".messages")) {
            p.sendMessage(msg.replace("&", "§"));
        }

        // FX: Particle
        boolean fw = cfg.getBoolean(path + ".event.firework", false);
        String snd = cfg.getString(path + ".event.sound", "");

        if (fw) {
            p.getWorld().spawnParticle(
                    Particle.SNOWFLAKE,
                    p.getLocation(),
                    30,
                    .5, .5, .5,
                    .01
            );
        }

        // FX: Sound (FIX APPLIED HERE)
        if (!snd.isEmpty()) {
            // Normalize the sound string (e.g., "entity_player_levelup" to "ENTITY_PLAYER_LEVELUP")
            String normalizedSound = snd.trim().toUpperCase(Locale.ROOT);

            // Use the string-based overload of playSound
            // This method handles the sound lookup internally without the deprecated Sound.valueOf()
            p.playSound(p.getLocation(), normalizedSound, 1f, 1f);

            // Note: If the sound key is invalid, this method typically fails silently
            // in modern Bukkit/Paper, which is a safer outcome than a crash.
        }
    }
}