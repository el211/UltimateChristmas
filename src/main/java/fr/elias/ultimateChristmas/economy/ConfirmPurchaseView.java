package fr.elias.ultimateChristmas.economy;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.Msg;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 3x9 confirmation GUI:
 *
 *  Slot layout (row=1):
 *   [1,2]  RED WOOL  -> cancel/back
 *   [1,4]  PREVIEW   -> shows what you're buying
 *   [1,6]  LIME WOOL -> confirm purchase
 *
 * YES flow:
 *   - check shards again
 *   - remove shards
 *   - if "commands:" in shop.yml -> run those console commands
 *   - else -> build direct custom item (lore, attributes, cosmetic durability text)
 *   - tell player new balance
 *
 * NO flow:
 *   - close confirm GUI and reopen ShardShopGUI
 */
public class ConfirmPurchaseView implements InventoryProvider {

    private final UltimateChristmas plugin;
    private final ShardManager shardManager;
    private final InventoryManager invManager;

    // which shop item are we confirming?
    private final String itemKey;   // e.g. "sword_of_light"
    private final String basePath;  // e.g. "items.sword_of_light"

    public ConfirmPurchaseView(UltimateChristmas plugin,
                               ShardManager shardManager,
                               InventoryManager invManager,
                               String itemKey) {
        this.plugin = plugin;
        this.shardManager = shardManager;
        this.invManager = invManager;
        this.itemKey = itemKey;
        this.basePath = "items." + itemKey;
    }

    /**
     * Opens the confirmation GUI for this itemKey for that player.
     */
    public void open(Player player) {
        SmartInventory.builder()
                .id("confirm-" + itemKey + "-" + player.getUniqueId())
                .provider(this)
                .size(3, 9)
                .title(Msg.color("&6Confirm purchase?"))
                .manager(invManager)
                .build()
                .open(player);
    }

    /**
     * Called by SmartInvs when the GUI is first created.
     */
    @Override
    public void init(Player player, InventoryContents contents) {
        FileConfiguration shopCfg = plugin.getConfig("shop.yml");

        final int cost = shopCfg.getInt(basePath + ".price", 0);
        final int balBefore = shardManager.getShards(player.getUniqueId());

        /*
         * CENTER PREVIEW ITEM
         * This is cosmetic: glow, lore, cost, durability preview, etc.
         */
        ItemStack preview = buildPreviewIcon(shopCfg, basePath, player, balBefore, cost);
        contents.set(1, 4, ClickableItem.empty(preview));

        /*
         * NO BUTTON (red wool)
         * Close confirm GUI and reopen the shop.
         */
        ItemStack noBtn = new ItemStack(Material.RED_WOOL);
        {
            ItemMeta m = noBtn.getItemMeta();
            if (m != null) {
                m.setDisplayName(Msg.color("&c&lNO - Cancel"));
                m.setLore(Collections.singletonList(
                        Msg.color("&7Go back without buying")
                ));
                noBtn.setItemMeta(m);
            }
        }

        contents.set(1, 2, ClickableItem.of(noBtn, e -> {
            player.closeInventory();
            // reopen main shop GUI
            new ShardShopGUI(plugin, shardManager, invManager).open(player);
        }));

        /*
         * YES BUTTON (lime wool)
         * Charge shards, give reward (command OR direct item), send success msg.
         */
        ItemStack yesBtn = new ItemStack(Material.LIME_WOOL);
        {
            ItemMeta m = yesBtn.getItemMeta();
            if (m != null) {
                m.setDisplayName(Msg.color("&a&lYES - Buy"));
                m.setLore(Arrays.asList(
                        Msg.color("&7Cost: &c" + cost + " &7shards"),
                        Msg.color("&7Balance: &a" + balBefore + " &7shards")
                ));
                yesBtn.setItemMeta(m);
            }
        }

        contents.set(1, 6, ClickableItem.of(yesBtn, e -> {
            plugin.getLogger().info(
                    "[UltimateChristmas] [CONFIRM GUI] " + player.getName()
                            + " confirming '" + itemKey + "' cost=" + cost
                            + " bal(before)=" + balBefore
            );

            // Step 1: Check shards + remove shards
            if (!shardManager.removeShards(player.getUniqueId(), cost)) {
                Msg.player(player,
                        plugin.getConfig().getString(
                                "messages.not-enough-shards",
                                "&cNot enough shards!"
                        )
                );
                player.closeInventory();
                return;
            }

            // Step 2: Give reward
            List<String> cmds = shopCfg.getStringList(basePath + ".commands");

            if (cmds != null && !cmds.isEmpty()) {
                // COMMAND MODE
                for (String rawCmd : cmds) {
                    String finalCmd = rawCmd.replace("%player%", player.getName());
                    plugin.getLogger().info(
                            "[UltimateChristmas] [CONFIRM GUI] running: " + finalCmd
                    );

                    try {
                        boolean ok = Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                finalCmd
                        );
                        if (!ok) {
                            plugin.getLogger().warning(
                                    "[UltimateChristmas] [CONFIRM GUI] command FAILED (returned false): " + finalCmd
                            );
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning(
                                "[UltimateChristmas] [CONFIRM GUI] command ERROR for "
                                        + finalCmd + " -> " + t
                        );
                        t.printStackTrace();
                    }
                }

            } else {
                // DIRECT ITEM MODE
                ItemStack reward = buildDirectReward(shopCfg, basePath);
                if (reward != null && reward.getType() != Material.AIR) {

                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);

                    // If inv is full, drop leftovers at the player's feet
                    if (!leftover.isEmpty()) {
                        leftover.values().forEach(st ->
                                player.getWorld().dropItemNaturally(player.getLocation(), st)
                        );
                    }

                    plugin.getLogger().info(
                            "[UltimateChristmas] [CONFIRM GUI] gave direct item for '"
                                    + itemKey + "' to " + player.getName()
                    );
                } else {
                    plugin.getLogger().warning(
                            "[UltimateChristmas] [CONFIRM GUI] direct item for '"
                                    + itemKey + "' was null/air, nothing given."
                    );
                }
            }

            // Step 3: Tell player their new shard balance
            int balAfter = shardManager.getShards(player.getUniqueId());
            String successMsg = plugin.getConfig().getString(
                    "messages.bought-item",
                    "&aPurchase successful! New balance: %balance% shards"
            );
            successMsg = successMsg.replace("%balance%", String.valueOf(balAfter));
            Msg.player(player, successMsg);

            // Step 4: close GUI
            player.closeInventory();
        }));
    }

    /**
     * SmartInvs tick update — not needed for static confirm GUI.
     */
    @Override
    public void update(Player player, InventoryContents contents) {
        // no live tick updates
    }

    /**
     * Build the PREVIEW item in the confirm GUI (the middle slot).
     *
     * Things it shows:
     *  - Display name from config
     *  - Lore from config (with any "Durability:" lines stripped out so we don't double them)
     *  - Optional cosmetic durability line from max_durability
     *  - The cost and the player's balance
     *  - Hidden enchants/attributes and fake glow
     */
    private ItemStack buildPreviewIcon(FileConfiguration cfg,
                                       String base,
                                       Player viewer,
                                       int bal,
                                       int price) {

        String iconName = cfg.getString(base + ".icon", "PAPER");
        Material mat = Material.matchMaterial(iconName);
        if (mat == null) {
            mat = Material.PAPER;
        }

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // Name
            String display = cfg.getString(base + ".display_name", "&cItem");
            meta.setDisplayName(Msg.color(display));

            // Build lore
            List<String> rawLore = cfg.getStringList(base + ".lore");
            List<String> built = new ArrayList<>();

            // Copy config lore but SKIP any line that already starts with "durability:"
            // (case-insensitive) so we don't end up with two.
            for (String l : rawLore) {
                if (l.toLowerCase().startsWith("durability:")) continue;
                built.add(Msg.color(l));
            }

            // Optional cosmetic durability preview from config.
            // We ONLY show this as text. We do NOT try to set real damage here.
            if (cfg.contains(base + ".max_durability")) {
                int customDurLeft = cfg.getInt(base + ".max_durability");
                built.add(Msg.color("&f"));
                built.add(Msg.color("&7Durability: &f" + customDurLeft + " &7uses left"));
            }

            // Cost / balance section
            built.add(" ");
            built.add(Msg.color("&7Cost: &c" + price + " &7shards"));
            built.add(Msg.color("&7You: &a" + bal + " &7shards"));

            meta.setLore(built);

            // Hide enchant/attribute text to keep it clean
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            stack.setItemMeta(meta);
        }

        // Fake glow (unbreaking 1) so items look special in GUI
        stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);

        return stack;
    }

    /**
     * Build the REAL reward ItemStack for direct-item purchases (items without "commands:" in config).
     *
     * Supported keys in shop.yml:
     *
     *   icon: MATERIAL_NAME
     *   display_name: "&e&lSword of Light"
     *   lore:
     *     - "&7Grants power against the Grinch."
     *   custom_model_data: 100
     *   attackdamage: 10
     *   attackspeed: 10
     *   max_durability: 42
     *
     * Behavior:
     *  - Sets display name and lore
     *  - Appends ONE cosmetic durability line if max_durability is defined
     *    (we DO NOT try to match it to Minecraft's internal bar anymore)
     *  - Applies custom model data if present
     *  - Adds AttributeModifiers for damage/speed (MAINHAND)
     *  - Gives it an Unbreaking 1 enchant for the visual "glow"
     *  - Hides attributes/enchant lines in tooltip
     */
    private ItemStack buildDirectReward(FileConfiguration cfg, String base) {
        // 1. Material
        String iconName = cfg.getString(base + ".icon", "PAPER");
        Material mat = Material.matchMaterial(iconName);
        if (mat == null) {
            mat = Material.PAPER;
        }

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        // 2. Basic display props
        String display = cfg.getString(base + ".display_name", "&cItem");
        meta.setDisplayName(Msg.color(display));

        List<String> rawLore = cfg.getStringList(base + ".lore");
        List<String> builtLore = new ArrayList<>();
        for (String l : rawLore) {
            builtLore.add(Msg.color(l));
        }

        // 3. Custom model data
        if (cfg.contains(base + ".custom_model_data")) {
            meta.setCustomModelData(cfg.getInt(base + ".custom_model_data"));
        }

        // 4. Attribute modifiers (damage / speed)
        double dmgBonus = cfg.getDouble(base + ".attackdamage", 0.0);
        if (dmgBonus != 0.0) {
            try {
                AttributeModifier dmgMod = new AttributeModifier(
                        new NamespacedKey(plugin, "ucustom_attack_damage"),
                        dmgBonus,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, dmgMod);
            } catch (Throwable t) {
                Bukkit.getLogger().warning(
                        "[UltimateChristmas] Could not apply ATTACK_DAMAGE attribute: " + t
                );
                t.printStackTrace();
            }
        }

        double spdBonus = cfg.getDouble(base + ".attackspeed", 0.0);
        if (spdBonus != 0.0) {
            try {
                AttributeModifier spdMod = new AttributeModifier(
                        new NamespacedKey(plugin, "ucustom_attack_speed"),
                        spdBonus,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, spdMod);
            } catch (Throwable t) {
                Bukkit.getLogger().warning(
                        "[UltimateChristmas] Could not apply ATTACK_SPEED attribute: " + t
                );
                t.printStackTrace();
            }
        }

        // 5. Custom "uses left" durability (our system)
        //
        // We'll store this in PersistentData so we can decrement later on hit.
        //
        int customUsesLeft = cfg.getInt(base + ".max_durability", -1);

        if (customUsesLeft > -1) {
            // add prettified line in lore
            builtLore.add(" ");
            builtLore.add(Msg.color("&7Durability: &f" + customUsesLeft + " &7uses left"));

            // mark item as unbreakable so vanilla bar/tooltip goes away
            meta.setUnbreakable(true);

            // store current uses left in PDC
            var pdc = meta.getPersistentDataContainer();
            NamespacedKey keyUses = new NamespacedKey(plugin, "ucustom_uses_left");
            pdc.set(keyUses, org.bukkit.persistence.PersistentDataType.INTEGER, customUsesLeft);

            // also store a marker so we know this is one of our tracked weapons
            NamespacedKey keyFlag = new NamespacedKey(plugin, "ucustom_durability_item");
            pdc.set(keyFlag, org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
        }

        // push lore back after we've maybe added the durability line
        meta.setLore(builtLore);

        // 6. Cosmetic glow + hide junk
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE
        );

        stack.setItemMeta(meta);
        return stack;
    }

}
