package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.santa.SantaManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SantaPresentListener implements Listener {

    private final UltimateChristmas plugin;
    private final SantaManager santaManager;

    // This MUST match gifts.drop_item.display_name after colorizing (& -> §)
    private static final String PRESENT_NAME = "§c§lSanta's Present";

    public SantaPresentListener(UltimateChristmas plugin, SantaManager santaManager) {
        this.plugin = plugin;
        this.santaManager = santaManager;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        // Only right-click main hand, ignore offhand spam
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.hasDisplayName()) return;

        if (!PRESENT_NAME.equals(meta.getDisplayName())) {
            return; // not our present
        }

        // It's Santa's Present!
        event.setCancelled(true);

        // Consume ONE present from stack
        int amount = item.getAmount();
        if (amount <= 1) {
            event.getPlayer().getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amount - 1);
        }

        // Give reward using the SAME logic Santa uses when clicked
        santaManager.tryGiveSantaGift(event.getPlayer());
    }
}
