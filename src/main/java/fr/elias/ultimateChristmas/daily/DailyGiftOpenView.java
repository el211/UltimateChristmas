// fr/elias/ultimateChristmas/daily/DailyGiftOpenView.java
package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.util.Msg;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import fr.elias.ultimateChristmas.UltimateChristmas;

import java.util.Arrays;

public class DailyGiftOpenView implements InventoryProvider {

    private final InventoryManager invManager;
    private final DailyGiftManager giftManager;
    private final ItemStack previewGift;

    public DailyGiftOpenView(InventoryManager invManager,
                             DailyGiftManager giftManager,
                             ItemStack previewGift) {
        this.invManager = invManager;
        this.giftManager = giftManager;
        this.previewGift = previewGift == null ? new ItemStack(Material.KNOWLEDGE_BOOK) : previewGift.clone();
    }

    public void open(Player player) {
        SmartInventory.builder()
                .id("daily-gift-confirm-" + player.getUniqueId())
                .provider(this)
                .size(3, 9)
                .title(Msg.color("&6Open your &eDaily Gift&6?"))
                .manager(invManager)
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Center: the gift item
        ItemStack center = previewGift.clone();
        ItemMeta m = center.getItemMeta();
        if (m != null) {
            m.setDisplayName(Msg.color("&6&lDaily Gift"));
            m.setLore(Arrays.asList(
                    Msg.color("&7Right here, right now."),
                    Msg.color("&7Open it to receive a reward!")
            ));
            center.setItemMeta(m);
        }
        contents.set(1, 4, ClickableItem.empty(center));

        // Left: Cancel (red wool)
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cm = cancel.getItemMeta();
        if (cm != null) {
            cm.setDisplayName(Msg.color("&c&lNo, not now"));
            cancel.setItemMeta(cm);
        }
        contents.set(1, 2, ClickableItem.of(cancel, e -> e.getWhoClicked().closeInventory()));

        // Right: Confirm (lime wool)
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta ym = confirm.getItemMeta();
        if (ym != null) {
            ym.setDisplayName(Msg.color("&a&lYes, open it!"));
            confirm.setItemMeta(ym);
        }
        contents.set(1, 6, ClickableItem.of(confirm, e -> {
            Player p = (Player) e.getWhoClicked();

            // Consume using config-aware matcher (daily_gifts.yml)
            boolean consumed = DailyGiftOpenViewUtil.consumeOneGift(UltimateChristmas.get(), p);
            if (!consumed) {
                p.sendMessage(Msg.color("&cYou don't seem to have the gift anymore."));
                p.closeInventory();
                return;
            }

            // Redeem!
            giftManager.redeemGift(p);
            p.closeInventory();
        }));

    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // no tick updates needed
    }
}
