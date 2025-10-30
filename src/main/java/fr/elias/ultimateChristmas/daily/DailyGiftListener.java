// fr/elias/ultimateChristmas/daily/DailyGiftListener.java
package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class DailyGiftListener implements Listener {

    private final UltimateChristmas plugin;
    private final DailyGiftManager giftManager;
    private final InventoryManager invManager;

    public DailyGiftListener(UltimateChristmas plugin,
                             DailyGiftManager giftManager,
                             InventoryManager invManager) {
        this.plugin = plugin;
        this.giftManager = giftManager;
        this.invManager = invManager;
    }

    // DailyGiftListener.java (replace onRightClick with this)
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) return;

        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        if (!GiftItemFactory.isGiftItem(inHand, cfg)) return;

        plugin.getLogger().info("[DailyGift] Detected daily gift item in " + p.getName() + " hand. Opening confirm GUI.");
        e.setCancelled(true);

        ItemStack preview = inHand.clone();
        new DailyGiftOpenView(invManager, giftManager, preview).open(p);
    }

}
