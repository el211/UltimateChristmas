package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
// NOTE: We are not injecting Base64 custom head texture here (requires GameProfile).
// You can extend later if you want visual skulls.

import java.util.ArrayList;
import java.util.List;

public class GiftItemFactory {

    public static ItemStack buildGiftItem(FileConfiguration cfg) {
        String base = "daily-gifts.item.";

        Material mat = Material.matchMaterial(cfg.getString(base + "material", "PLAYER_HEAD"));
        if (mat == null) mat = Material.PLAYER_HEAD;

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(cfg.getString(base + "display_name", "&6Daily gift")));
            List<String> loreColored = new ArrayList<>();
            for (String l : cfg.getStringList(base + "lore")) {
                loreColored.add(Msg.color(l));
            }
            meta.setLore(loreColored);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean isGiftItem(ItemStack stack, FileConfiguration cfg) {
        if (stack == null) return false;
        if (!stack.hasItemMeta()) return false;
        if (!stack.getItemMeta().hasDisplayName()) return false;

        String expected = Msg.color(cfg.getString("daily-gifts.item.display_name", "&6Daily gift"));
        return stack.getItemMeta().getDisplayName().equals(expected);
    }
}
