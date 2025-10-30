package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
// add at top with other imports
import fr.elias.ultimateChristmas.UltimateChristmas;

final class DailyGiftOpenViewUtil {
    private DailyGiftOpenViewUtil() {}

    static boolean consumeOneGift(UltimateChristmas plugin, Player p)
    {
        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");

        // 1) main hand first
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (GiftItemFactory.isGiftItem(hand, cfg) && hand.getAmount() > 0) {
            hand.setAmount(hand.getAmount() - 1);
            plugin.getLogger().info("[DailyGift] Consumed one gift from main hand of " + p.getName());
            return true;
        }

        // 2) search any slot for a matching gift
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (GiftItemFactory.isGiftItem(st, cfg) && st.getAmount() > 0) {
                st.setAmount(st.getAmount() - 1);
                p.getInventory().setItem(i, st.getAmount() <= 0 ? null : st);
                plugin.getLogger().info("[DailyGift] Consumed one gift from slot " + i + " of " + p.getName());
                return true;
            }
        }

        plugin.getLogger().warning("[DailyGift] consumeOneGift: no matching DAILY item found in " + p.getName()
                + "'s inventory. Expected name="
                + GiftItemFactory.buildGiftItem(cfg).getItemMeta().getDisplayName());
        return false;
    }
}
