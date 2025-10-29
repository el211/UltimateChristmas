package fr.elias.ultimateChristmas.economy;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class ShardShopListener implements Listener {

    private final UltimateChristmas plugin;
    private final ShardShopGUI shop;

    public ShardShopListener(UltimateChristmas plugin, ShardShopGUI shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // If you later want per-player cleanup from ShardShopGUI (timers, etc),
        // this is your hook. Right now nothing is required.
    }
}
