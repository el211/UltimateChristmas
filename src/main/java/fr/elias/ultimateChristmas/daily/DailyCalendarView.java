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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * SmartInvs view that renders the Advent-style Daily Gifts calendar.
 * - Reads season start/length from daily_gifts.yml (daily-gifts.calendar.start.*).
 * - Shows days 1..length, marking PAST / TODAY / FUTURE.
 * - Shows the reward icon for TODAY if configured (group override supported).
 * - Clicking TODAY (when unclaimed) triggers DailyGiftManager.redeemGift(player).
 * - Claimed state persists via DailyProgressStore.
 */
public class DailyCalendarView implements InventoryProvider {

    private final InventoryManager invManager;
    private final DailyGiftManager giftManager;
    private final DailyProgressStore store;

    public DailyCalendarView(InventoryManager invManager,
                             DailyGiftManager giftManager,
                             DailyProgressStore store) {
        this.invManager = invManager;
        this.giftManager = giftManager;
        this.store = store;
    }

    public void open(Player player) {
        SmartInventory.builder()
                .id("uc-daily-calendar-" + player.getUniqueId())
                .provider(this)
                .size(6, 9)
                .title(Msg.color("&6&lAdvent Calendar"))
                .manager(invManager)
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        final FileConfiguration cfg = UltimateChristmas.get().getConfig("daily_gifts.yml");

        // Title row decoration
        for (int c = 0; c < 9; c++) {
            contents.set(0, c, ClickableItem.empty(named(Material.RED_STAINED_GLASS_PANE, "&c&lDaily Gifts")));
        }

        // Determine today's season index (1..length) or -1 if outside season
        LocalDate today = store.todayLocalDate();
        int todayIdx = store.seasonDayIndex(today); // relies on calendar.start in config
        int calendarLength = Math.max(1, cfg.getInt("daily-gifts.calendar.start.length", 25));

        // Layout
        int startRow = 1; // start placing days from row 1
        int maxDaysShown = (6 - startRow) * 9;
        int lastDay = Math.min(calendarLength, maxDaysShown);

        int shown = 0;
        for (int day = 1; day <= lastDay; day++) {
            int row = startRow + (shown / 9);
            int col = shown % 9;
            shown++;

            ItemStack icon;
            List<String> lore = new ArrayList<>();
            boolean clickable = false;

            if (todayIdx < 0) {
                // Calendar disabled (we're outside the season window)
                icon = named(Material.GRAY_STAINED_GLASS_PANE, "&7Day " + day + " (Locked)");
                lore.add(Msg.color("&7Calendar mode disabled (outside season)."));
            } else {
                // Try to resolve a reward key for this calendar slot from config:
                String group = LuckPermsHook.getPrimaryGroupOrDefault(player, "default");
                String key = resolveCalendarKeyForDay(cfg, group, day);

                // Determine state relative to today
                int state; // -1 = past, 0 = today, 1 = future
                if (day < todayIdx) state = -1;
                else if (day == todayIdx) state = 0;
                else state = 1;

                // Map this calendar day to a real date for claimed check
                LocalDate thatDate = today.plusDays(day - todayIdx);
                long epochOfThisDay = thatDate.toEpochDay();
                boolean claimed = store.hasClaimedEpochDay(player.getUniqueId(), epochOfThisDay);

                if (state < 0) {
                    icon = named(Material.RED_STAINED_GLASS_PANE, "&cDay " + day + " &7(Closed)");
                    lore.add(Msg.color("&7Past day."));
                } else if (state > 0) {
                    icon = named(Material.GRAY_STAINED_GLASS_PANE, "&7Day " + day + " (Locked)");
                    lore.add(Msg.color("&7Come back on that day."));
                } else {
                    // Today
                    if (claimed) {
                        icon = named(Material.RED_STAINED_GLASS_PANE, "&cDay " + day + " &7(Claimed)");
                        lore.add(Msg.color("&7Already claimed today."));
                    } else {
                        // Show actual reward’s icon (fallbacks to PAPER if not set)
                        RewardVisual visual = resolveRewardVisual(cfg, group, key);
                        Material mat = Material.matchMaterial(visual.material.toUpperCase(Locale.ROOT));
                        if (mat == null) mat = Material.PAPER;

                        icon = named(mat, "&aDay " + day + " &7(Available)");
                        if (visual.name != null && !visual.name.isEmpty()) {
                            lore.add(Msg.color(visual.name));
                        }
                        for (String l : visual.lore) lore.add(Msg.color(l));
                        lore.add(Msg.color("&f "));
                        lore.add(Msg.color("&eClick to claim!"));
                        clickable = true;
                    }
                }
            }

            // Apply lore
            ItemMeta im = icon.getItemMeta();
            if (im != null) {
                im.setLore(lore);
                icon.setItemMeta(im);
            }

            if (clickable) {
                contents.set(row, col, ClickableItem.of(icon, e -> {
                    UUID u = e.getWhoClicked().getUniqueId();
                    // Only allow if it's still today and unclaimed
                    if (store.hasClaimedToday(u)) {
                        e.getWhoClicked().sendMessage(Msg.color("&cYou already claimed today."));
                        return;
                    }
                    giftManager.redeemGift((Player) e.getWhoClicked());
                    // Refresh GUI to reflect claimed state
                    open((Player) e.getWhoClicked());
                }));
            } else {
                contents.set(row, col, ClickableItem.empty(icon));
            }
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // no-op
    }

    /* ------------------------------------------------------------------------
     * Helpers
     * --------------------------------------------------------------------- */

    private static ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(name));
            it.setItemMeta(meta);
        }
        return it;
    }

    /**
     * Resolve reward key for a given calendar day slot, checking:
     *  1) daily-gifts.calendar.<group>.<day>
     *  2) daily-gifts.calendar.<day>
     * Returns null if no mapping.
     */
    private static String resolveCalendarKeyForDay(FileConfiguration cfg, String group, int day) {
        String k1 = "daily-gifts.calendar." + group + "." + day;
        String forced = cfg.getString(k1, null);
        if (forced != null && !forced.isEmpty()) return forced;

        String k2 = "daily-gifts.calendar." + day;
        forced = cfg.getString(k2, null);
        return (forced == null || forced.isEmpty()) ? null : forced;
    }

    /**
     * Resolve the icon/name/lore for a reward key out of the rewards section.
     * Looks in the player's group section first, then falls back to "default".
     */
    private static RewardVisual resolveRewardVisual(FileConfiguration cfg, String group, String key) {
        if (key == null) return RewardVisual.fallback();

        String baseGroup = "daily-gifts.rewards." + group + "." + key + ".icon";
        String baseDefault = "daily-gifts.rewards.default." + key + ".icon";

        ConfigurationSection sec = cfg.getConfigurationSection(baseGroup);
        if (sec == null) sec = cfg.getConfigurationSection(baseDefault);

        if (sec == null) return RewardVisual.fallback();

        String material = sec.getString("material", "PAPER");
        String name = sec.getString("name", "");
        List<String> lore = sec.getStringList("lore");
        if (lore == null) lore = new ArrayList<>();

        return new RewardVisual(material, name, lore);
    }

    /* Simple holder for icon metadata */
    private static final class RewardVisual {
        final String material;
        final String name;
        final List<String> lore;

        RewardVisual(String material, String name, List<String> lore) {
            this.material = material == null ? "PAPER" : material;
            this.name = name == null ? "" : name;
            this.lore = lore == null ? new ArrayList<>() : lore;
        }

        static RewardVisual fallback() {
            return new RewardVisual("PAPER", "&7Reward", new ArrayList<>());
        }
    }
}
