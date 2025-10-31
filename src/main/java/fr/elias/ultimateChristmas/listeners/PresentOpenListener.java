package fr.elias.ultimateChristmas.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.santa.SantaManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class PresentOpenListener implements Listener {

    private final UltimateChristmas plugin;
    private final SantaManager santaManager;
    private final NamespacedKey PRESENT_KEY;

    public PresentOpenListener(UltimateChristmas plugin, SantaManager santaManager) {
        this.plugin = plugin;
        this.santaManager = santaManager;
        this.PRESENT_KEY = new NamespacedKey(plugin, "uc_present");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClickPresent(PlayerInteractEvent e) {
        // Only main hand interaction (avoid double-fire with offhand)
        if (e.getHand() != EquipmentSlot.HAND) return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack inHand = e.getItem();
        if (inHand == null) return;

        // ✅ Use the helper so legacy gifts are accepted and auto-retagged
        if (!isPresentItemAndRetag(e.getPlayer(), inHand)) return;

        // Region gating
        if (!canOpenHere(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }

        // Consume one present
        e.setCancelled(true);
        if (inHand.getAmount() > 1) {
            inHand.setAmount(inHand.getAmount() - 1);
        } else {
            e.getPlayer().getInventory().setItemInMainHand(null);
        }

        // Roll / execute weighted reward set
        santaManager.tryGiveSantaGift(e.getPlayer());

        // Feedback
        var p = e.getPlayer();
        try { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.15f); } catch (Exception ignored) {}
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1, 0), 20, .3, .4, .3, .05);
    }

    /** Accept new PDC-tagged presents and legacy name/material presents; retag legacy items. */
    private boolean isPresentItemAndRetag(Player p, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();

        // New format: PDC tag
        if (meta.getPersistentDataContainer().has(PRESENT_KEY, PersistentDataType.BYTE)) return true;

        // Legacy fallback: match configured display name + material
        var cfg = plugin.getConfig("santa.yml");
        var dropSec = cfg.getConfigurationSection("gifts.drop_item");
        if (dropSec == null) return false;

        String expectedName = dropSec.getString("display_name", "&c&lSanta's Present").replace("&","§");
        String matName = dropSec.getString("material", "PLAYER_HEAD");
        Material expectedMat = Material.matchMaterial(matName);
        if (expectedMat == null) expectedMat = Material.PLAYER_HEAD;

        boolean nameMatches = meta.hasDisplayName() && expectedName.equals(meta.getDisplayName());
        boolean matMatches  = stack.getType() == expectedMat;

        if (nameMatches && matMatches) {
            // Auto-retag so it survives future restarts
            meta.getPersistentDataContainer().set(PRESENT_KEY, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
            if (plugin.getConfig().getBoolean("debug", false) || cfg.getBoolean("debug", false)) {
                plugin.getLogger().info("[PresentOpen] Retagged legacy present in " + p.getName() + "'s inventory.");
            }
            return true;
        }
        return false;
    }

    /** Optional WorldGuard gating for where presents can be opened. */
    private boolean canOpenHere(Player p) {
        var cfg = plugin.getConfig("santa.yml");
        boolean enabled = cfg.getBoolean("gifts.open_in_region.enabled", false);
        if (!enabled) return true;

        List<String> raw = cfg.getStringList("gifts.open_in_region.allowed_regions");
        String denyMsg = cfg.getString("gifts.open_in_region.deny_message",
                "&cYou can only open Santa's presents near the Christmas tree!");
        if (raw == null || raw.isEmpty()) return true;

        Set<String> allowed = raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        try {
            var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            var adapted = BukkitAdapter.adapt(p.getLocation());
            ApplicableRegionSet set = query.getApplicableRegions(adapted);

            boolean inside = set.getRegions().stream()
                    .map(ProtectedRegion::getId)
                    .map(id -> id.toLowerCase(Locale.ROOT))
                    .anyMatch(allowed::contains);

            if (!inside) p.sendMessage(denyMsg.replace("&", "§"));
            return inside;
        } catch (Throwable t) {
            plugin.getLogger().warning("[PresentOpen] WG query failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            p.sendMessage(denyMsg.replace("&", "§"));
            return false;
        }
    }
}
