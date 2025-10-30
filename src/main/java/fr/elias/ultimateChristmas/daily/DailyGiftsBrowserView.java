package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.Msg;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class DailyGiftsBrowserView implements InventoryProvider {

    private final UltimateChristmas plugin;
    private final InventoryManager invManager;
    private final DailyGiftManager giftManager;
    private final int page;

    public DailyGiftsBrowserView(UltimateChristmas plugin, InventoryManager invManager, DailyGiftManager giftManager, int page) {
        this.plugin = plugin;
        this.invManager = invManager;
        this.giftManager = giftManager;
        this.page = Math.max(0, page);
    }

    public void open(Player player) {
        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        String title = Msg.color(cfg.getString("gui.title", "&6Daily Gifts"));
        int rows = Math.max(1, Math.min(6, cfg.getInt("gui.rows", 6)));

        dbg("Opening DailyGiftsBrowserView for " + player.getName() + " page=" + page + " rows=" + rows);

        SmartInventory.builder()
                .id("daily-gifts-browser-" + player.getUniqueId())
                .size(rows, 9)
                .title(title)
                .provider(this)
                .manager(invManager)
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        int rows = contents.inventory().getRows();
        ConfigurationSection gui = cfg.getConfigurationSection("gui");
        boolean requireItem = cfg.getBoolean("gui.require_gift_item", false);

        Material borderMat = matOr("GRAY_STAINED_GLASS_PANE",
                gui == null ? null : gui.getString("border.material"));

        // Border
        if (cfg.getBoolean("gui.border.enabled", true)) {
            ItemStack pane = named(borderMat, " ");
            for (int col = 0; col < 9; col++) {
                contents.set(0, col, ClickableItem.empty(pane));
                contents.set(rows - 1, col, ClickableItem.empty(pane));
            }
            for (int r = 1; r < rows - 1; r++) {
                contents.set(r, 0, ClickableItem.empty(pane));
                contents.set(r, 8, ClickableItem.empty(pane));
            }
        }

        // Rewards by group (fallback default)
        String group = LuckPermsHook.getPrimaryGroupOrDefault(player, "default");
        String base = "daily-gifts.rewards." + group;
        ConfigurationSection sec = cfg.getConfigurationSection(base);
        if (sec == null) {
            base = "daily-gifts.rewards.default";
            sec = cfg.getConfigurationSection(base);
        }
        List<String> rewardIds = (sec == null) ? Collections.emptyList() : new ArrayList<>(sec.getKeys(false));
        rewardIds.sort(String::compareToIgnoreCase);

        int perPage = Math.max(1, cfg.getInt("gui.rewards_per_page", 21));
        List<String> pageSlice = rewardIds.stream()
                .skip((long) page * perPage)
                .limit(perPage)
                .collect(Collectors.toList());

        // Slots area
        List<int[]> slots = parseSlots(cfg.getStringList("gui.reward_slots"));
        if (slots.isEmpty()) {
            for (int r = 1; r < rows - 1; r++) {
                for (int c = 1; c < 8; c++) {
                    slots.add(new int[]{r, c});
                }
            }
        }

        boolean alreadyClaimed = !giftManager.canClaimNow(player);

        // Fill reward cards
        for (int i = 0; i < pageSlice.size() && i < slots.size(); i++) {
            String id = pageSlice.get(i);
            String path = base + "." + id;

            int weight = cfg.getInt(path + ".chance", 1);
            List<String> cmds = cfg.getStringList(path + ".commands");
            Material icon = matOr("PAPER", cfg.getString(path + ".icon", "PAPER"));

            ItemStack it = new ItemStack(icon);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.setDisplayName(Msg.color("&e" + id));
                List<String> lore = new ArrayList<>();
                lore.add(Msg.color("&7Weight: &f" + weight));
                if (!cmds.isEmpty()) {
                    lore.add(Msg.color("&7Commands:"));
                    for (int k = 0; k < Math.min(3, cmds.size()); k++) lore.add(Msg.color("&8- &7" + cmds.get(k)));
                    if (cmds.size() > 3) lore.add(Msg.color("&8..."));
                }
                lore.add(Msg.color(requireItem
                        ? "&8Click to open the Gift confirmation"
                        : (alreadyClaimed ? "&7Already claimed today" : "&aClick to claim today's reward")));
                im.setLore(lore);
                it.setItemMeta(im);
            }

            int[] pos = slots.get(i);
            if (requireItem) {
                contents.set(pos[0], pos[1], ClickableItem.of(it, e -> {
                    Player p = (Player) e.getWhoClicked();
                    dbg("Reward card clicked (item-mode): " + id + " by " + p.getName());
                    ItemStack preview = GiftItemFactory.buildGiftItem(plugin.getConfig("daily_gifts.yml"));
                    new DailyGiftOpenView(invManager, giftManager, preview).open(p);
                }));
            } else {
                // Advent mode: click any card to claim (if not yet claimed)
                contents.set(pos[0], pos[1], ClickableItem.of(it, e -> {
                    Player p = (Player) e.getWhoClicked();
                    dbg("Reward card clicked (advent): " + id + " by " + p.getName());
                    giftManager.claimFromGUI(p);
                    p.closeInventory();
                }));
            }
        }

        // Pagination + Open buttons
        int totalPages = (int) Math.ceil(rewardIds.size() / (double) perPage);
        boolean hasPrev = page > 0;
        boolean hasNext = page + 1 < totalPages;

        Material prevMat = matOr("ARROW", gui == null ? null : gui.getString("prev.material"));
        Material nextMat = matOr("ARROW", gui == null ? null : gui.getString("next.material"));
        Material openMat = matOr("LIME_WOOL", gui == null ? null : gui.getString("open.material"));

        // Prev
        ItemStack prev = named(prevMat, hasPrev ? "&aPrevious" : "&7Previous");
        contents.set(rows - 1, 2, ClickableItem.of(prev, e -> {
            if (hasPrev) new DailyGiftsBrowserView(plugin, invManager, giftManager, page - 1).open((Player) e.getWhoClicked());
        }));

        // Open button
        ItemStack openBtn;
        if (!requireItem) {
            // Advent mode
            if (alreadyClaimed) {
                openBtn = named(Material.GRAY_WOOL, "&7Already claimed",
                        "&7Next in &f" + giftManager.timeUntilResetText());
                contents.set(rows - 1, 4, ClickableItem.empty(openBtn));
            } else {
                openBtn = named(openMat, "&a&lClaim Today's Gift");
                contents.set(rows - 1, 4, ClickableItem.of(openBtn, e -> {
                    Player p = (Player) e.getWhoClicked();
                    dbg("Open (advent) clicked by " + p.getName());
                    giftManager.claimFromGUI(p);
                    p.closeInventory();
                }));
            }
        } else {
            // Item mode -> opens confirmation view
            openBtn = named(openMat, "&a&lOpen a Daily Gift", "&7(Requires the gift item)");
            contents.set(rows - 1, 4, ClickableItem.of(openBtn, e -> {
                Player p = (Player) e.getWhoClicked();
                dbg("Open (item-mode) clicked by " + p.getName());
                ItemStack preview = GiftItemFactory.buildGiftItem(plugin.getConfig("daily_gifts.yml"));
                new DailyGiftOpenView(invManager, giftManager, preview).open(p);
            }));
        }

        // Next
        ItemStack next = named(nextMat, hasNext ? "&aNext" : "&7Next");
        contents.set(rows - 1, 6, ClickableItem.of(next, e -> {
            if (hasNext) new DailyGiftsBrowserView(plugin, invManager, giftManager, page + 1).open((Player) e.getWhoClicked());
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    /* ------------------------ helpers ------------------------ */

    private List<int[]> parseSlots(List<String> list) {
        List<int[]> out = new ArrayList<>();
        if (list == null) return out;
        for (String s : list) {
            String[] parts = s.split(",");
            if (parts.length != 2) continue;
            try {
                int r = Integer.parseInt(parts[0].trim());
                int c = Integer.parseInt(parts[1].trim());
                if (r >= 0 && r < 6 && c >= 0 && c < 9) out.add(new int[]{r, c});
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private Material matOr(String def, String name) {
        Material m = Material.matchMaterial(name == null ? def : name);
        return m == null ? Material.matchMaterial(def) : m;
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(Msg.color(name));
            if (lore != null && lore.length > 0) {
                List<String> ll = new ArrayList<>();
                for (String l : lore) ll.add(Msg.color(l));
                im.setLore(ll);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private boolean debugEnabled() {
        try {
            if (plugin.getConfig("daily_gifts.yml").getBoolean("daily-gifts.debug", false)) return true;
        } catch (Throwable ignored) {}
        try {
            return plugin.getConfig().getBoolean("debug", false);
        } catch (Throwable ignored) {}
        return false;
    }

    private void dbg(String s) { if (debugEnabled()) plugin.getLogger().info("[DailyGUI] " + s); }
}
