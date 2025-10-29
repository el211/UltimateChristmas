package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CustomDurabilityListener implements Listener {

    private final UltimateChristmas plugin;
    private final NamespacedKey keyUses;
    private final NamespacedKey keyFlag;

    public CustomDurabilityListener(UltimateChristmas plugin) {
        this.plugin = plugin;
        this.keyUses = new NamespacedKey(plugin, "ucustom_uses_left");
        this.keyFlag = new NamespacedKey(plugin, "ucustom_durability_item");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // Only care if attacker is a player doing melee
        if (!(event.getDamager() instanceof Player p)) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check marker
        Byte marker = pdc.get(keyFlag, PersistentDataType.BYTE);
        if (marker == null || marker != (byte)1) {
            // not one of our tracked items
            return;
        }

        // Read uses left
        Integer usesLeft = pdc.get(keyUses, PersistentDataType.INTEGER);
        if (usesLeft == null) {
            // somehow missing, stop tracking
            return;
        }

        // Decrement
        usesLeft = usesLeft - 1;

        if (usesLeft <= 0) {
            // break the item
            p.getInventory().setItemInMainHand(null);
            Msg.player(p, "&cYour &f" + (meta.hasDisplayName() ? meta.getDisplayName() : "item") + " &chas shattered!");
            return;
        }

        // Still has uses -> update stored value
        pdc.set(keyUses, PersistentDataType.INTEGER, usesLeft);

        // Update lore to reflect the new number
        List<String> oldLore = meta.getLore();
        if (oldLore == null) oldLore = new ArrayList<>();

        List<String> newLore = new ArrayList<>();
        boolean replaced = false;

        for (String line : oldLore) {
            String stripped = Msg.color(line).replace("§", "&"); // normalize color section sign -> &
            // We look for the durability line we added:
            // "&7Durability: &f" + number + " &7uses left"
            // We don't want to double-add, we want to replace.
            if (stripped.contains("Durability:") && stripped.contains("uses left")) {
                newLore.add(Msg.color("&7Durability: &f" + usesLeft + " &7uses left"));
                replaced = true;
            } else {
                newLore.add(line);
            }
        }

        if (!replaced) {
            // edge case: somehow the lore didn't have our line yet
            newLore.add(Msg.color("&7Durability: &f" + usesLeft + " &7uses left"));
        }

        meta.setLore(newLore);

        // Write meta (with updated PDC + lore) back to the stack
        hand.setItemMeta(meta);

        // Optional: let player know when it's low
        if (usesLeft == 5 || usesLeft == 3 || usesLeft == 1) {
            Msg.player(p, "&eYour " +
                    (meta.hasDisplayName() ? meta.getDisplayName() : "item") +
                    " &eis about to break (&f" + usesLeft + "&e uses left)!");
        }
    }
}
