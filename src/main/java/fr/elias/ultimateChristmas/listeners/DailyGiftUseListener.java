package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.daily.DailyGiftManager;
import fr.elias.ultimateChristmas.daily.GiftItemFactory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class DailyGiftUseListener implements Listener {

    private final UltimateChristmas plugin;
    private final DailyGiftManager dailyGiftManager;

    public DailyGiftUseListener(UltimateChristmas plugin, DailyGiftManager mgr) {
        this.plugin = plugin;
        this.dailyGiftManager = mgr;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT_CLICK")) return;
        ItemStack it = e.getItem();
        if (it == null) return;

        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        if (!GiftItemFactory.isGiftItem(it, cfg)) return;

        // cancel vanilla use
        e.setCancelled(true);

        // give reward
        dailyGiftManager.redeemGift(e.getPlayer());

        // consume
        it.setAmount(it.getAmount() - 1);
    }
}
