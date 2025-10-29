package fr.elias.ultimateChristmas.economy;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.Msg;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Shard Shop GUI.
 *
 * Opens with /shards shop.
 * Shows all items from shop.yml under "items:".
 *
 * Clicking an item will open ConfirmPurchaseView for that item,
 * which then handles YES/NO, shard deduction, and reward delivery.
 */
public class ShardShopGUI implements InventoryProvider {

    private final UltimateChristmas plugin;
    private final ShardManager shardManager;
    private final InventoryManager invManager;
    private final String title;

    // used to add cosmetic glint to menu icons
    private final Enchantment GLOW_ENCHANTMENT;

    public ShardShopGUI(UltimateChristmas plugin,
                        ShardManager shardManager,
                        InventoryManager invManager) {

        this.plugin = plugin;
        this.shardManager = shardManager;
        this.invManager = invManager;

        this.title = Msg.color(
                plugin.getConfig("shop.yml")
                        .getString("shop-title", "&c&lShard Shop")
        );

        // Try modern name first
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (enchantment == null) {
            // Fallback legacy name on older Spigot/Paper
            enchantment = Enchantment.getByName("DURABILITY");
        }
        this.GLOW_ENCHANTMENT = enchantment;
    }

    /**
     * Open the main shop GUI for a player.
     */
    public void open(Player p) {
        SmartInventory.builder()
                .id("shardshop-" + p.getUniqueId())
                .provider(this)
                .size(6, 9) // 6 rows of 9 = 54 slots
                .title(title)
                .manager(invManager)
                .build()
                .open(p);
    }

    /**
     * Expose InventoryManager so other views (like ConfirmPurchaseView)
     * can reuse it if needed.
     */
    public InventoryManager getInvManager() {
        return invManager;
    }

    @Override
    public void init(Player player, InventoryContents contents) {

        FileConfiguration shopCfg = plugin.getConfig("shop.yml");
        ConfigurationSection itemsSec = shopCfg.getConfigurationSection("items");
        if (itemsSec == null) {
            return;
        }

        for (String key : itemsSec.getKeys(false)) {
            String basePath = "items." + key;

            int slot = shopCfg.getInt(basePath + ".slot", -1);
            if (slot < 0 || slot > 53) continue;

            int row = slot / 9;
            int col = slot % 9;

            // Build the icon that shows price, your balance, etc.
            ItemStack icon = buildIcon(shopCfg, basePath, player);

            // When the player clicks the icon -> open confirmation GUI
            ClickableItem clickable = ClickableItem.of(icon, e -> {
                ConfirmPurchaseView confirmGui = new ConfirmPurchaseView(
                        plugin,
                        shardManager,
                        invManager,
                        key // e.g. "diamond_item", "sword_of_light"
                );
                confirmGui.open(player);
            });

            contents.set(row, col, clickable);
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // We don't need per-tick refresh; we rebuild lore on click/confirm.
    }

    /**
     * Builds the shop display icon for a single item entry in shop.yml.
     *
     * Lore layout:
     *   - the item's configured lore
     *   - "Cost: X shards"
     *   - "You: Y shards"
     *   - "Click to buy..."
     *
     * Also adds a glow using UNBREAKING so menu items look special.
     */
    private ItemStack buildIcon(FileConfiguration cfg, String basePath, Player viewer) {

        // Material / icon
        String matName = cfg.getString(basePath + ".icon", "PAPER");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            mat = Material.PAPER;
        }

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // Display name
            String display = cfg.getString(basePath + ".display_name", "&cItem");
            meta.setDisplayName(Msg.color(display));

            // Base lore from config
            List<String> rawLore = cfg.getStringList(basePath + ".lore");
            List<String> lore = new ArrayList<>(rawLore.size() + 5);

            for (String l : rawLore) {
                lore.add(Msg.color(l));
            }

            // Price + balance
            int price = cfg.getInt(basePath + ".price", 0);
            int bal = shardManager.getShards(viewer.getUniqueId());

            lore.add(" ");
            lore.add(Msg.color("&7Cost: &c" + price + " &7shards"));
            lore.add(Msg.color("&7You: &a" + bal + " &7shards"));
            lore.add(" ");
            lore.add(Msg.color("&eClick to buy..."));

            meta.setLore(lore);

            // custom model data (optional cosmetic for resource packs)
            if (cfg.contains(basePath + ".custom_model_data")) {
                meta.setCustomModelData(cfg.getInt(basePath + ".custom_model_data"));
            }

            // hide enchant / attributes so preview look is clean
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            stack.setItemMeta(meta);

            // add cosmetic glint
            if (GLOW_ENCHANTMENT != null) {
                stack.addUnsafeEnchantment(GLOW_ENCHANTMENT, 1);
            }
        }

        return stack;
    }
}
