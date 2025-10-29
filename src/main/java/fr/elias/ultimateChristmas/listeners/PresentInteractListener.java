package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.WeightedRandomPicker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PresentInteractListener implements Listener {

    private final UltimateChristmas plugin;

    public PresentInteractListener(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only care about main hand right-click
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() == null) return;

        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // Must be a player head
        if (item.getType() != Material.PLAYER_HEAD) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String displayName = meta.getDisplayName();
        if (displayName == null) return;

        // Check if this is "Santa's Present" head
        // You can tighten this check however you want.
        String stripped = displayName.replace("§", "").toLowerCase(Locale.ROOT);
        if (!stripped.contains("santa") || !stripped.contains("present")) {
            return;
        }

        // Prevent block placing / interaction
        event.setCancelled(true);

        // Consume ONE of the present heads
        item.setAmount(item.getAmount() - 1);

        // Roll reward using the same logic as SantaManager.tryGiveSantaGift()
        FileConfiguration cfg = plugin.getConfig("santa.yml");
        ConfigurationSection giftsSec = cfg.getConfigurationSection("gifts.rewards");
        if (giftsSec == null) {
            player.sendMessage("§cThe present is empty... weird.");
            return;
        }

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String giftKey : giftsSec.getKeys(false)) {
            String path = "gifts.rewards." + giftKey;
            if (cfg.isConfigurationSection(path)) {
                int weight = cfg.getInt(path + ".chance", 1);
                picker.add(giftKey, weight);
            }
        }

        String chosen = picker.pick();
        if (chosen == null) {
            player.sendMessage("§cThe present crumbled to dust...");
            return;
        }

        String basePath = "gifts.rewards." + chosen;

        // 1) console commands
        List<String> commands = cfg.getStringList(basePath + ".commands");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName())
            );
        }

        // 2) message
        String prefix = plugin.getConfig("config.yml")
                .getString("messages.prefix", "")
                .replace("&", "§");

        List<String> messages = cfg.getStringList(basePath + ".messages");
        for (String line : messages) {
            player.sendMessage(prefix + line.replace("&", "§"));
        }

        // 3) fx
        boolean fw = cfg.getBoolean(basePath + ".event.firework", false);
        String soundKey = cfg.getString(basePath + ".event.sound", "");

        if (fw) {
            player.getWorld().spawnParticle(
                    Particle.FIREWORK,
                    player.getLocation(),
                    20,
                    0.4, 0.4, 0.4,
                    0.1
            );
        }

        if (soundKey != null && !soundKey.isEmpty()) {
            String normalizedSound = soundKey.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace(' ', '_');
            try {
                player.playSound(player.getLocation(), normalizedSound, 1f, 1f);
            } catch (Exception ignored) {}
        }
    }
}
