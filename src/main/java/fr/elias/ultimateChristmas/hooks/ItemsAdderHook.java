package fr.elias.ultimateChristmas.hooks;

import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

public final class ItemsAdderHook {
    private ItemsAdderHook() {}

    public static boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /** Returns namespace:id of a placed IA block or null if not IA block. */
    public static String getNamespacedId(Block b) {
        try {
            CustomBlock cb = CustomBlock.byAlreadyPlaced(b);
            if (cb == null) return null;
            String id = cb.getNamespacedID();
            return (id == null || id.isBlank()) ? null : id;
        } catch (Throwable t) {
            return null;
        }
    }
}
