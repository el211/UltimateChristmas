// src/main/java/fr/elias/ultimateChristmas/santa/SantaPresentFactory.java
package fr.elias.ultimateChristmas.santa;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class SantaPresentFactory {
    private SantaPresentFactory(){}

    public static ItemStack buildPresent(UltimateChristmas plugin, int amount) {
        var cfg = plugin.getConfig("santa.yml").getConfigurationSection("gifts.drop_item");

        // Fallbacks are same as your Santa drop config
        String matName = cfg != null ? cfg.getString("material", "PLAYER_HEAD") : "PLAYER_HEAD";
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.PLAYER_HEAD;

        String name = cfg != null ? cfg.getString("display_name", "&c&lSanta's Present") : "&c&lSanta's Present";
        List<String> lore = cfg != null ? cfg.getStringList("lore") : List.of("&7Right-click to open!");

        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name.replace('&','§'));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(l.replace('&','§'));
                meta.setLore(colored);
            }
            // IMPORTANT: mark as Santa Present so PresentOpenListener recognizes it after restart
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "uc_present"),
                    PersistentDataType.BYTE, (byte)1);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
